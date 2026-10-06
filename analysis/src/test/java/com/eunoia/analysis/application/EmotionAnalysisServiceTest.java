package com.eunoia.analysis.application;

import com.eunoia.analysis.application.dto.EmotionAnalysisInfo;
import com.eunoia.analysis.application.dto.EmotionScorePoint;
import com.eunoia.analysis.domain.AnalysisStatus;
import com.eunoia.analysis.domain.EmotionAnalysis;
import com.eunoia.analysis.domain.EmotionAnalysisRepository;
import com.eunoia.analysis.domain.EmotionAnalysisResult;
import com.eunoia.analysis.domain.EmotionAnalyzer;
import com.eunoia.common.exception.BusinessException;
import com.eunoia.journal.event.EmotionEntryDeleted;
import com.eunoia.journal.event.EmotionEntryRecorded;
import com.eunoia.journal.query.EmotionEntryQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmotionAnalysisServiceTest {

    private static final LocalDate ENTRY_DATE = LocalDate.of(2026, 9, 20);

    @Mock
    private EmotionAnalysisRepository emotionAnalysisRepository;

    @Mock
    private EmotionAnalysisResultRecorder resultRecorder;

    @Mock
    private EmotionAnalyzer emotionAnalyzer;

    @Mock
    private EmotionEntryQueryApi emotionEntryQueryApi;

    private EmotionAnalysisService emotionAnalysisService;

    @BeforeEach
    void setUp() {
        emotionAnalysisService = new EmotionAnalysisService(emotionAnalysisRepository, resultRecorder, emotionAnalyzer, emotionEntryQueryApi);
    }

    @Test
    @DisplayName("일기 작성 이벤트를 받으면 PROCESSING 상태의 분석 행을 저장한다.")
    void start_savesProcessingAnalysis() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, ENTRY_DATE);
        ArgumentCaptor<EmotionAnalysis> captor = ArgumentCaptor.forClass(EmotionAnalysis.class);
        when(emotionAnalysisRepository.save(captor.capture())).thenAnswer(invocation -> captor.getValue());

        emotionAnalysisService.start(event);

        assertThat(captor.getValue().getEntryId()).isEqualTo(1L);
        assertThat(captor.getValue().getMemberId()).isEqualTo(2L);
        assertThat(captor.getValue().getEntryDate()).isEqualTo(ENTRY_DATE);
        assertThat(captor.getValue().getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
        assertThat(captor.getValue().getEmotionDetected()).isNull();
        verifyNoInteractions(emotionAnalyzer, resultRecorder);
    }

    @Test
    @DisplayName("처리 중인 분석이 있으면 GPT를 호출해서 성공 결과를 기록한다.")
    void handle_withProcessingAnalysis_analyzesAndRecordsSuccess() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, ENTRY_DATE);
        EmotionAnalysisResult result = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));

        when(emotionAnalysisRepository.findByEntryId(1L))
                .thenReturn(Optional.of(EmotionAnalysis.start(1L, 2L, ENTRY_DATE)));
        when(emotionEntryQueryApi.findContent(2L, 1L)).thenReturn(Optional.of("오늘 하루"));
        when(emotionAnalyzer.analyze("오늘 하루")).thenReturn(result);

        emotionAnalysisService.handle(event);

        verify(resultRecorder).recordSuccess(1L, result);
        verify(resultRecorder, never()).recordFailure(any(), any());
        verify(emotionAnalysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("분석 행이 없으면(삭제된 글 포함) GPT를 호출하지 않고 건너뛴다.")
    void handle_withNoAnalysisRow_skipsProcessing() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, ENTRY_DATE);

        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.empty());

        emotionAnalysisService.handle(event);

        verify(emotionAnalyzer, never()).analyze(any());
        verifyNoInteractions(resultRecorder);
    }

    @Test
    @DisplayName("이미 SUCCESS인 분석은 다시 처리하지 않는다(이벤트 재전달 멱등).")
    void handle_withSuccessAnalysis_skipsProcessing() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, ENTRY_DATE);
        EmotionAnalysis existing = EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));

        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.of(existing));

        emotionAnalysisService.handle(event);

        verify(emotionAnalyzer, never()).analyze(any());
        verifyNoInteractions(resultRecorder);
    }

    @Test
    @DisplayName("이미 FAILED인 분석은 다시 처리하지 않는다(종결 상태).")
    void handle_withFailedAnalysis_skipsProcessing() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, ENTRY_DATE);
        EmotionAnalysis existing = EmotionAnalysis.fail(1L, 2L, ENTRY_DATE, "실패 사유");

        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.of(existing));

        emotionAnalysisService.handle(event);

        verify(emotionAnalyzer, never()).analyze(any());
        verifyNoInteractions(resultRecorder);
    }

    @Test
    @DisplayName("본문을 조회할 수 없으면(이미 삭제된 글) GPT를 호출하지 않고 건너뛴다.")
    void handle_withDeletedEntry_skipsAnalysis() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, ENTRY_DATE);

        when(emotionAnalysisRepository.findByEntryId(1L))
                .thenReturn(Optional.of(EmotionAnalysis.start(1L, 2L, ENTRY_DATE)));
        when(emotionEntryQueryApi.findContent(2L, 1L)).thenReturn(Optional.empty());

        emotionAnalysisService.handle(event);

        verify(emotionAnalyzer, never()).analyze(any());
        verifyNoInteractions(resultRecorder);
    }

    @Test
    @DisplayName("도메인 검증에 한 번 실패해도 재시도해서 성공하면 성공 결과를 기록한다.")
    void handle_whenValidationFailsOnce_retriesAndRecordsSuccess() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, ENTRY_DATE);
        EmotionAnalysisResult invalidResult = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", -1.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));
        EmotionAnalysisResult validResult = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));

        when(emotionAnalysisRepository.findByEntryId(1L))
                .thenReturn(Optional.of(EmotionAnalysis.start(1L, 2L, ENTRY_DATE)));
        when(emotionEntryQueryApi.findContent(2L, 1L)).thenReturn(Optional.of("오늘 하루"));
        when(emotionAnalyzer.analyze("오늘 하루")).thenReturn(invalidResult, validResult);

        emotionAnalysisService.handle(event);

        verify(emotionAnalyzer, times(2)).analyze("오늘 하루");
        verify(resultRecorder).recordSuccess(1L, validResult);
        verify(resultRecorder, never()).recordFailure(any(), any());
    }

    @Test
    @DisplayName("도메인 검증에 최대 횟수(3회)만큼 계속 실패하면 GPT를 정확히 3번 호출하고 실패를 기록한다.")
    void handle_whenValidationAlwaysFails_recordsFailureAfterMaxAttempts() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, ENTRY_DATE);
        EmotionAnalysisResult invalidResult = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", -1.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));

        when(emotionAnalysisRepository.findByEntryId(1L))
                .thenReturn(Optional.of(EmotionAnalysis.start(1L, 2L, ENTRY_DATE)));
        when(emotionEntryQueryApi.findContent(2L, 1L)).thenReturn(Optional.of("오늘 하루"));
        when(emotionAnalyzer.analyze("오늘 하루")).thenReturn(invalidResult);

        emotionAnalysisService.handle(event);

        verify(emotionAnalyzer, times(3)).analyze("오늘 하루");
        verify(resultRecorder).recordFailure(eq(1L), anyString());
        verify(resultRecorder, never()).recordSuccess(any(), any());
    }

    @Test
    @DisplayName("GPT 호출 자체가 실패하면 재시도 없이 즉시 실패를 기록한다.")
    void handle_whenAnalyzerThrowsRuntimeException_recordsFailureImmediately() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, ENTRY_DATE);

        when(emotionAnalysisRepository.findByEntryId(1L))
                .thenReturn(Optional.of(EmotionAnalysis.start(1L, 2L, ENTRY_DATE)));
        when(emotionEntryQueryApi.findContent(2L, 1L)).thenReturn(Optional.of("오늘 하루"));
        when(emotionAnalyzer.analyze("오늘 하루")).thenThrow(new RuntimeException("GPT 호출 실패"));

        emotionAnalysisService.handle(event);

        verify(emotionAnalyzer, times(1)).analyze("오늘 하루");
        verify(resultRecorder).recordFailure(1L, "GPT 호출 실패");
        verify(resultRecorder, never()).recordSuccess(any(), any());
    }

    @Test
    @DisplayName("실패 원인 메시지가 없으면 '원인 불명'으로 기록한다.")
    void handle_whenFailureHasNoMessage_recordsUnknownReason() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, ENTRY_DATE);

        when(emotionAnalysisRepository.findByEntryId(1L))
                .thenReturn(Optional.of(EmotionAnalysis.start(1L, 2L, ENTRY_DATE)));
        when(emotionEntryQueryApi.findContent(2L, 1L)).thenReturn(Optional.of("오늘 하루"));
        when(emotionAnalyzer.analyze("오늘 하루")).thenThrow(new RuntimeException());

        emotionAnalysisService.handle(event);

        verify(resultRecorder).recordFailure(1L, "원인 불명");
    }

    @Test
    @DisplayName("실패 원인 메시지가 컬럼 길이(1000자)를 넘으면 잘라서 기록한다.")
    void handle_whenFailureMessageTooLong_truncatesReason() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, ENTRY_DATE);
        String longMessage = "x".repeat(1500);

        when(emotionAnalysisRepository.findByEntryId(1L))
                .thenReturn(Optional.of(EmotionAnalysis.start(1L, 2L, ENTRY_DATE)));
        when(emotionEntryQueryApi.findContent(2L, 1L)).thenReturn(Optional.of("오늘 하루"));
        when(emotionAnalyzer.analyze("오늘 하루")).thenThrow(new RuntimeException(longMessage));

        emotionAnalysisService.handle(event);

        verify(resultRecorder).recordFailure(1L, "x".repeat(1000));
    }

    @Test
    @DisplayName("삭제 이벤트를 받으면 그 글의 분석을 소프트 삭제한다.")
    void handleDeleted_withExistingAnalysis_softDeletesAnalysis() {
        EmotionAnalysis existing = EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));

        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.of(existing));

        emotionAnalysisService.handle(EmotionEntryDeleted.of(1L, 2L, LocalDateTime.now()));

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(emotionAnalysisRepository, never()).save(any()); // 트랜잭션 안 변경 감지로 반영 — save 불필요
    }

    @Test
    @DisplayName("삭제 이벤트를 받았는데 분석이 아직 없으면 아무것도 하지 않는다.")
    void handleDeleted_withNoAnalysis_doesNothing() {
        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.empty());

        emotionAnalysisService.handle(EmotionEntryDeleted.of(1L, 2L, LocalDateTime.now()));

        verify(emotionAnalysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 분석을 조회하면 예외가 발생한다.")
    void getByEntryId_withNonExistentAnalysis_throws() {
        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emotionAnalysisService.getByEntryId(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("분석 결과를 찾을 수 없어요.");
    }

    @Test
    @DisplayName("처리 중인 분석을 조회하면 PROCESSING 상태와 비어있는 결과를 그대로 반환한다.")
    void getByEntryId_withProcessingAnalysis_returnsProcessingInfo() {
        when(emotionAnalysisRepository.findByEntryId(1L))
                .thenReturn(Optional.of(EmotionAnalysis.start(1L, 2L, ENTRY_DATE)));

        EmotionAnalysisInfo info = emotionAnalysisService.getByEntryId(1L, 2L);

        assertThat(info.entryId()).isEqualTo(1L);
        assertThat(info.status()).isEqualTo(AnalysisStatus.PROCESSING);
        assertThat(info.emotionDetected()).isNull();
        assertThat(info.emotionScore()).isNull();
        assertThat(info.warmMessages()).isNull();
    }

    @Test
    @DisplayName("다른 회원의 처리 중인 분석은 PROCESSING이어도 조회할 수 없다.")
    void getByEntryId_withProcessingAnalysisOfOtherMember_throwsForbidden() {
        when(emotionAnalysisRepository.findByEntryId(1L))
                .thenReturn(Optional.of(EmotionAnalysis.start(1L, 2L, ENTRY_DATE)));

        assertThatThrownBy(() -> emotionAnalysisService.getByEntryId(1L, 99L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("소유자가 아닌 회원이 조회하면 예외가 발생한다.")
    void getByEntryId_withNonOwner_throws() {
        EmotionAnalysis analysis = EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));
        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.of(analysis));

        assertThatThrownBy(() -> emotionAnalysisService.getByEntryId(1L, 999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("소유자가 조회하면 정상적으로 반환한다.")
    void getByEntryId_withOwner_returnsInfo() {
        EmotionAnalysis analysis = EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));
        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.of(analysis));

        EmotionAnalysisInfo result = emotionAnalysisService.getByEntryId(1L, 2L);

        assertThat(result.entryId()).isEqualTo(1L);
        assertThat(result.memberId()).isEqualTo(2L);
        assertThat(result.status()).isEqualTo(AnalysisStatus.SUCCESS);
        assertThat(result.emotionDetected()).isEqualTo("평온");
        assertThat(result.warmMessages()).containsExactly("문장1", "문장2", "문장3");
    }

    @Test
    @DisplayName("최신 분석이 존재하면 반환한다.")
    void getLatest_withExistingAnalysis_returnsInfo() {
        EmotionAnalysis analysis = EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));
        when(emotionAnalysisRepository.findTopByMemberIdAndStatusInOrderByEntryDateDescEntryIdDesc(2L, List.of(AnalysisStatus.SUCCESS, AnalysisStatus.FAILED)))
                .thenReturn(Optional.of(analysis));

        Optional<EmotionAnalysisInfo> result = emotionAnalysisService.getLatest(2L);

        assertThat(result).isPresent();
        assertThat(result.get().entryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("최신 분석 조회는 PROCESSING을 제외한 종결 상태(SUCCESS·FAILED)만 대상으로 한다.")
    void getLatest_queriesOnlyTerminalStatuses() {
        when(emotionAnalysisRepository.findTopByMemberIdAndStatusInOrderByEntryDateDescEntryIdDesc(2L, List.of(AnalysisStatus.SUCCESS, AnalysisStatus.FAILED)))
                .thenReturn(Optional.empty());

        emotionAnalysisService.getLatest(2L);

        ArgumentCaptor<List<AnalysisStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(emotionAnalysisRepository).findTopByMemberIdAndStatusInOrderByEntryDateDescEntryIdDesc(eq(2L), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(AnalysisStatus.SUCCESS, AnalysisStatus.FAILED)
                .doesNotContain(AnalysisStatus.PROCESSING);
    }

    @Test
    @DisplayName("가장 최근 분석이 FAILED여도 그대로 반환한다(대시보드가 실패 화면을 보여준다).")
    void getLatest_withFailedAnalysis_returnsFailedInfo() {
        EmotionAnalysis failed = EmotionAnalysis.fail(1L, 2L, ENTRY_DATE, "실패 사유");
        when(emotionAnalysisRepository.findTopByMemberIdAndStatusInOrderByEntryDateDescEntryIdDesc(2L, List.of(AnalysisStatus.SUCCESS, AnalysisStatus.FAILED)))
                .thenReturn(Optional.of(failed));

        Optional<EmotionAnalysisInfo> result = emotionAnalysisService.getLatest(2L);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    @DisplayName("분석이 하나도 없으면 최신 분석 조회는 빈 값을 반환한다.")
    void getLatest_withNoAnalyses_returnsEmpty() {
        when(emotionAnalysisRepository.findTopByMemberIdAndStatusInOrderByEntryDateDescEntryIdDesc(2L, List.of(AnalysisStatus.SUCCESS, AnalysisStatus.FAILED)))
                .thenReturn(Optional.empty());

        Optional<EmotionAnalysisInfo> result = emotionAnalysisService.getLatest(2L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("감정 점수 목록은 SUCCESS 상태만, entryDate 오름차순으로 조회한다.")
    void getScores_returnsAscendingByEntryDate() {
        EmotionAnalysis later = EmotionAnalysis.create(1L, 2L, LocalDate.of(2026, 9, 20), "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));
        EmotionAnalysis earlier = EmotionAnalysis.create(2L, 2L, LocalDate.of(2026, 9, 10), "평온", "평온,안정",
                "요약", "흐름", "감정요약", 60.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));
        // 리포지토리는 entryDate 내림차순으로 반환(신규 메서드 이름 그대로) — 서비스가 오름차순으로 뒤집어야 함
        when(emotionAnalysisRepository.findTop7ByMemberIdAndStatusOrderByEntryDateDescEntryIdDesc(2L, AnalysisStatus.SUCCESS))
                .thenReturn(new ArrayList<>(List.of(later, earlier)));

        List<EmotionScorePoint> result = emotionAnalysisService.getScores(2L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).entryId()).isEqualTo(2L);
        assertThat(result.get(0).entryDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(result.get(1).entryId()).isEqualTo(1L);
        assertThat(result.get(1).entryDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    @DisplayName("분석이 하나도 없으면 감정 점수 목록은 빈 리스트를 반환한다.")
    void getScores_withNoAnalyses_returnsEmptyList() {
        when(emotionAnalysisRepository.findTop7ByMemberIdAndStatusOrderByEntryDateDescEntryIdDesc(2L, AnalysisStatus.SUCCESS))
                .thenReturn(new ArrayList<>());

        List<EmotionScorePoint> result = emotionAnalysisService.getScores(2L);

        assertThat(result).isEmpty();
    }
}
