package com.eunoia.analysis.application;

import com.eunoia.analysis.application.dto.EmotionAnalysisInfo;
import com.eunoia.analysis.domain.AnalysisStatus;
import com.eunoia.analysis.domain.EmotionAnalysis;
import com.eunoia.analysis.domain.EmotionAnalysisRepository;
import com.eunoia.analysis.domain.EmotionAnalysisResult;
import com.eunoia.analysis.domain.EmotionAnalyzer;
import com.eunoia.common.exception.BusinessException;
import com.eunoia.journal.event.EmotionEntryRecorded;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
    private EmotionAnalyzer emotionAnalyzer;

    private EmotionAnalysisService emotionAnalysisService;

    @BeforeEach
    void setUp() {
        emotionAnalysisService = new EmotionAnalysisService(emotionAnalysisRepository, emotionAnalyzer);
    }

    @Test
    @DisplayName("새로운 엔트리에 대한 이벤트를 받으면 GPT를 호출해서 분석 결과를 저장한다.")
    void handle_withNewEntry_savesAnalysis() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, "오늘 하루", ENTRY_DATE);
        EmotionAnalysisResult result = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));

        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.empty());
        when(emotionAnalyzer.analyze("오늘 하루")).thenReturn(result);
        ArgumentCaptor<EmotionAnalysis> captor = ArgumentCaptor.forClass(EmotionAnalysis.class);
        when(emotionAnalysisRepository.save(captor.capture())).thenAnswer(invocation -> captor.getValue());

        emotionAnalysisService.handle(event);

        assertThat(captor.getValue().getEntryId()).isEqualTo(1L);
        assertThat(captor.getValue().getMemberId()).isEqualTo(2L);
        assertThat(captor.getValue().getEntryDate()).isEqualTo(ENTRY_DATE);
        assertThat(captor.getValue().getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
        assertThat(captor.getValue().getEmotionDetected()).isEqualTo("평온");
        assertThat(captor.getValue().getWarmMessages()).containsExactly("문장1", "문장2", "문장3");
    }

    @Test
    @DisplayName("이미 분석이 존재하는 엔트리는 GPT를 호출하지 않고 건너뛴다.")
    void handle_withExistingAnalysis_skipsProcessing() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, "오늘 하루", ENTRY_DATE);
        EmotionAnalysis existing = EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));

        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.of(existing));

        emotionAnalysisService.handle(event);

        verify(emotionAnalyzer, never()).analyze(any());
        verify(emotionAnalysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("도메인 검증에 한 번 실패해도 재시도해서 성공하면 SUCCESS로 저장한다.")
    void handle_whenValidationFailsOnce_retriesAndSucceeds() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, "오늘 하루", ENTRY_DATE);
        EmotionAnalysisResult invalidResult = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", -1.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));
        EmotionAnalysisResult validResult = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));

        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.empty());
        when(emotionAnalyzer.analyze("오늘 하루")).thenReturn(invalidResult, validResult);
        ArgumentCaptor<EmotionAnalysis> captor = ArgumentCaptor.forClass(EmotionAnalysis.class);
        when(emotionAnalysisRepository.save(captor.capture())).thenAnswer(invocation -> captor.getValue());

        emotionAnalysisService.handle(event);

        verify(emotionAnalyzer, times(2)).analyze("오늘 하루");
        assertThat(captor.getValue().getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
        assertThat(captor.getValue().getEmotionScore()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("도메인 검증에 최대 횟수만큼 계속 실패하면 FAILED로 저장한다.")
    void handle_whenValidationAlwaysFails_savesFailedAfterMaxAttempts() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, "오늘 하루", ENTRY_DATE);
        EmotionAnalysisResult invalidResult = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", -1.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));

        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.empty());
        when(emotionAnalyzer.analyze("오늘 하루")).thenReturn(invalidResult);
        ArgumentCaptor<EmotionAnalysis> captor = ArgumentCaptor.forClass(EmotionAnalysis.class);
        when(emotionAnalysisRepository.save(captor.capture())).thenAnswer(invocation -> captor.getValue());

        emotionAnalysisService.handle(event);

        verify(emotionAnalyzer, times(3)).analyze("오늘 하루");
        assertThat(captor.getValue().getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(captor.getValue().getEntryId()).isEqualTo(1L);
        assertThat(captor.getValue().getEntryDate()).isEqualTo(ENTRY_DATE);
    }

    @Test
    @DisplayName("GPT 호출 자체가 실패하면 재시도 없이 즉시 FAILED로 저장한다.")
    void handle_whenAnalyzerThrowsRuntimeException_savesFailedImmediately() {
        EmotionEntryRecorded event = EmotionEntryRecorded.of(1L, 2L, "오늘 하루", ENTRY_DATE);

        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.empty());
        when(emotionAnalyzer.analyze("오늘 하루")).thenThrow(new RuntimeException("GPT 호출 실패"));
        ArgumentCaptor<EmotionAnalysis> captor = ArgumentCaptor.forClass(EmotionAnalysis.class);
        when(emotionAnalysisRepository.save(captor.capture())).thenAnswer(invocation -> captor.getValue());

        emotionAnalysisService.handle(event);

        verify(emotionAnalyzer, times(1)).analyze("오늘 하루");
        assertThat(captor.getValue().getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(captor.getValue().getFailureReason()).isEqualTo("GPT 호출 실패");
    }

    @Test
    @DisplayName("존재하지 않는 분석을 조회하면 예외가 발생한다.")
    void getByEntryId_withNonExistentAnalysis_throws() {
        when(emotionAnalysisRepository.findByEntryId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emotionAnalysisService.getByEntryId(1L, 2L))
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
}
