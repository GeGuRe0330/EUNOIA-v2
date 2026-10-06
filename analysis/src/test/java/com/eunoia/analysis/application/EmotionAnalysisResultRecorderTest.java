package com.eunoia.analysis.application;

import com.eunoia.analysis.domain.AnalysisStatus;
import com.eunoia.analysis.domain.EmotionAnalysis;
import com.eunoia.analysis.domain.EmotionAnalysisRepository;
import com.eunoia.analysis.domain.EmotionAnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class EmotionAnalysisResultRecorderTest {

    private static final LocalDate ENTRY_DATE = LocalDate.of(2026, 9, 20);

    private static final EmotionAnalysisResult RESULT = new EmotionAnalysisResult(
            "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
            List.of("문장1", "문장2", "문장3"));

    @Mock
    private EmotionAnalysisRepository emotionAnalysisRepository;

    private EmotionAnalysisResultRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new EmotionAnalysisResultRecorder(emotionAnalysisRepository);
    }

    @Test
    @DisplayName("처리 중인 분석에 성공 결과를 기록하면 SUCCESS가 되고 결과가 저장된다.")
    void recordSuccess_withProcessingAnalysis_completes() {
        EmotionAnalysis processing = EmotionAnalysis.start(1L, 2L, ENTRY_DATE);
        when(emotionAnalysisRepository.findByEntryIdForUpdate(1L)).thenReturn(Optional.of(processing));

        recorder.recordSuccess(1L, RESULT);

        assertThat(processing.getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
        assertThat(processing.getEmotionDetected()).isEqualTo("평온");
        assertThat(processing.getEmotionScore()).isEqualTo(80.0);
        assertThat(processing.getWarmMessages()).containsExactly("문장1", "문장2", "문장3");
        verify(emotionAnalysisRepository, never()).save(any()); // 트랜잭션 안 변경 감지로 반영 — save 불필요
    }

    @Test
    @DisplayName("분석 행이 없으면(처리 중 삭제됨) 성공 결과를 버리고 아무것도 하지 않는다.")
    void recordSuccess_withNoRow_discardsResult() {
        when(emotionAnalysisRepository.findByEntryIdForUpdate(1L)).thenReturn(Optional.empty());

        recorder.recordSuccess(1L, RESULT);

        verify(emotionAnalysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 FAILED(종결)인 분석에는 늦게 도착한 성공 결과를 반영하지 않는다.")
    void recordSuccess_withFailedAnalysis_discardsResult() {
        EmotionAnalysis failed = EmotionAnalysis.fail(1L, 2L, ENTRY_DATE, "시간 초과");
        when(emotionAnalysisRepository.findByEntryIdForUpdate(1L)).thenReturn(Optional.of(failed));

        recorder.recordSuccess(1L, RESULT);

        assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("시간 초과");
        assertThat(failed.getEmotionDetected()).isNull();
    }

    @Test
    @DisplayName("이미 SUCCESS(종결)인 분석은 다시 기록해도 바뀌지 않는다.")
    void recordSuccess_withSuccessAnalysis_keepsExistingResult() {
        EmotionAnalysis success = EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "불안", "불안",
                "요약2", "흐름2", "감정요약2", 10.0, 20, "이유", List.of("a", "b", "c"));
        when(emotionAnalysisRepository.findByEntryIdForUpdate(1L)).thenReturn(Optional.of(success));

        recorder.recordSuccess(1L, RESULT);

        assertThat(success.getEmotionDetected()).isEqualTo("불안");
        assertThat(success.getEmotionScore()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("처리 중인 분석에 실패를 기록하면 FAILED가 되고 사유가 저장된다.")
    void recordFailure_withProcessingAnalysis_marksFailed() {
        EmotionAnalysis processing = EmotionAnalysis.start(1L, 2L, ENTRY_DATE);
        when(emotionAnalysisRepository.findByEntryIdForUpdate(1L)).thenReturn(Optional.of(processing));

        recorder.recordFailure(1L, "GPT 호출 실패");

        assertThat(processing.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(processing.getFailureReason()).isEqualTo("GPT 호출 실패");
    }

    @Test
    @DisplayName("분석 행이 없으면 실패 기록을 버리고 아무것도 하지 않는다.")
    void recordFailure_withNoRow_doesNothing() {
        when(emotionAnalysisRepository.findByEntryIdForUpdate(1L)).thenReturn(Optional.empty());

        recorder.recordFailure(1L, "GPT 호출 실패");

        verify(emotionAnalysisRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 SUCCESS인 분석은 실패 기록으로 덮어쓰지 않는다.")
    void recordFailure_withSuccessAnalysis_keepsSuccess() {
        EmotionAnalysis success = EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));
        when(emotionAnalysisRepository.findByEntryIdForUpdate(1L)).thenReturn(Optional.of(success));

        recorder.recordFailure(1L, "GPT 호출 실패");

        assertThat(success.getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
        assertThat(success.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("멈춘 처리 확정은 timeout만큼 이전에 생성된 PROCESSING 분석을 고정 사유로 FAILED 처리하고 건수를 반환한다.")
    void failStuck_failsProcessingCreatedBeforeThreshold() {
        when(emotionAnalysisRepository.failProcessingCreatedBefore(any(LocalDateTime.class), eq("처리 시간 초과")))
                .thenReturn(2);
        LocalDateTime before = LocalDateTime.now();

        int failed = recorder.failStuck(Duration.ofMinutes(5));

        LocalDateTime after = LocalDateTime.now();
        assertThat(failed).isEqualTo(2);
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(emotionAnalysisRepository).failProcessingCreatedBefore(captor.capture(), eq("처리 시간 초과"));
        assertThat(captor.getValue()).isBetween(before.minusMinutes(5), after.minusMinutes(5));
    }

    @Test
    @DisplayName("멈춘 처리가 없으면 0건을 반환한다.")
    void failStuck_withNothingStuck_returnsZero() {
        when(emotionAnalysisRepository.failProcessingCreatedBefore(any(LocalDateTime.class), any())).thenReturn(0);

        assertThat(recorder.failStuck(Duration.ofMinutes(5))).isZero();
    }
}
