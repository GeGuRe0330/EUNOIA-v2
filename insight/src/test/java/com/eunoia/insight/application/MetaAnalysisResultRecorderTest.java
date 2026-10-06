package com.eunoia.insight.application;

import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisGenerationStatus;
import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisResultRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaAnalysisResultRecorderTest {

    private static final LocalDate PERIOD_END = LocalDate.of(2026, 10, 6);

    @Mock
    private MetaAnalysisResultRepository metaAnalysisResultRepository;

    private MetaAnalysisResultRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new MetaAnalysisResultRecorder(metaAnalysisResultRepository);
    }

    private MetaAnalysisResult processing() {
        return MetaAnalysisResult.start(1L, PERIOD_END.minusDays(29), PERIOD_END, 1, 10, 0, List.of(1L, 2L));
    }

    private MetaAnalysisContent content(String summary) {
        return new MetaAnalysisContent(
                new MetaAnalysisContent.Outer(summary, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new MetaAnalysisContent.Inner("내면", List.of(), List.of(), List.of(), List.of(), "", ""),
                new MetaAnalysisContent.Clarity(80, List.of(), List.of(), List.of()),
                List.of());
    }

    @Test
    @DisplayName("처리 중인 행에 성공을 기록하면 SUCCESS가 되고 내용이 저장된다(잠금 조회 사용).")
    void recordSuccess_withProcessingRow_completes() {
        MetaAnalysisResult row = processing();
        when(metaAnalysisResultRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(row));

        recorder.recordSuccess(5L, content("겉모습 요약"));

        assertThat(row.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.SUCCESS);
        assertThat(row.getContent().outer().summary()).isEqualTo("겉모습 요약");
    }

    @Test
    @DisplayName("행이 없으면 성공 결과를 버리고 아무것도 하지 않는다.")
    void recordSuccess_withMissingRow_discards() {
        when(metaAnalysisResultRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

        recorder.recordSuccess(5L, content("요약"));
    }

    @Test
    @DisplayName("이미 FAILED(종결)인 행에는 늦게 도착한 성공 결과를 반영하지 않는다.")
    void recordSuccess_withFailedRow_discards() {
        MetaAnalysisResult row = processing();
        row.markFailed("시간 초과");
        when(metaAnalysisResultRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(row));

        recorder.recordSuccess(5L, content("늦게 온 결과"));

        assertThat(row.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.FAILED);
        assertThat(row.getContent()).isNull();
        assertThat(row.getFailureReason()).isEqualTo("시간 초과");
    }

    @Test
    @DisplayName("이미 SUCCESS인 행은 다시 기록해도 바뀌지 않는다.")
    void recordSuccess_withSuccessRow_keepsExistingContent() {
        MetaAnalysisResult row = processing();
        row.complete(content("처음 결과"));
        when(metaAnalysisResultRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(row));

        recorder.recordSuccess(5L, content("다른 결과"));

        assertThat(row.getContent().outer().summary()).isEqualTo("처음 결과");
    }

    @Test
    @DisplayName("처리 중인 행에 실패를 기록하면 FAILED가 되고 사유가 저장된다.")
    void recordFailure_withProcessingRow_marksFailed() {
        MetaAnalysisResult row = processing();
        when(metaAnalysisResultRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(row));

        recorder.recordFailure(5L, "GPT 호출 실패");

        assertThat(row.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.FAILED);
        assertThat(row.getFailureReason()).isEqualTo("GPT 호출 실패");
    }

    @Test
    @DisplayName("이미 SUCCESS인 행은 실패 기록으로 덮어쓰지 않는다.")
    void recordFailure_withSuccessRow_keepsSuccess() {
        MetaAnalysisResult row = processing();
        row.complete(content("결과"));
        when(metaAnalysisResultRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(row));

        recorder.recordFailure(5L, "GPT 호출 실패");

        assertThat(row.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.SUCCESS);
        assertThat(row.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("멈춘 시도 확정은 timeout만큼 이전에 생성된 PROCESSING 시도를 고정 사유로 FAILED 처리하고 건수를 반환한다.")
    void failStuck_failsProcessingCreatedBeforeThreshold() {
        when(metaAnalysisResultRepository.failProcessingCreatedBefore(any(LocalDateTime.class), eq("처리 시간 초과")))
                .thenReturn(2);
        LocalDateTime before = LocalDateTime.now();

        int failed = recorder.failStuck(Duration.ofMinutes(3));

        LocalDateTime after = LocalDateTime.now();
        assertThat(failed).isEqualTo(2);
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(metaAnalysisResultRepository).failProcessingCreatedBefore(captor.capture(), eq("처리 시간 초과"));
        assertThat(captor.getValue()).isBetween(before.minusMinutes(3), after.minusMinutes(3));
    }

    @Test
    @DisplayName("멈춘 시도가 없으면 0건을 반환한다.")
    void failStuck_withNothingStuck_returnsZero() {
        when(metaAnalysisResultRepository.failProcessingCreatedBefore(any(LocalDateTime.class), any())).thenReturn(0);

        assertThat(recorder.failStuck(Duration.ofMinutes(3))).isZero();
    }
}
