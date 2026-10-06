package com.eunoia.insight.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetaAnalysisResultTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 9, 7);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 10, 6);
    private static final List<Long> ENTRY_IDS = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

    private MetaAnalysisContent content(String summary) {
        return new MetaAnalysisContent(
                new MetaAnalysisContent.Outer(summary, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new MetaAnalysisContent.Inner("내면", List.of(), List.of(), List.of(), List.of(), "", ""),
                new MetaAnalysisContent.Clarity(80, List.of(), List.of(), List.of()),
                List.of());
    }

    private MetaAnalysisResult processing() {
        return MetaAnalysisResult.start(1L, PERIOD_START, PERIOD_END, 1, 10, 3, ENTRY_IDS);
    }

    @Test
    @DisplayName("기존 create로 만든 결과는 SUCCESS이고 attemptNo는 1이다(기존 경로 회귀 방지).")
    void create_setsSuccessAndFirstAttempt() {
        MetaAnalysisResult result = MetaAnalysisResult.create(1L, PERIOD_START, PERIOD_END, 10, 3, content("요약"));

        assertThat(result.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.SUCCESS);
        assertThat(result.getAttemptNo()).isEqualTo(1);
        assertThat(result.getContent()).isNotNull();
        assertThat(result.getSelectedEntryIds()).isNull();
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("start로 만들면 PROCESSING이고 내용·실패 사유는 비어있으며 선택된 일기 목록과 시도 번호가 저장된다.")
    void start_withValidArguments_setProcessing() {
        MetaAnalysisResult result = processing();

        assertThat(result.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.PROCESSING);
        assertThat(result.getMemberId()).isEqualTo(1L);
        assertThat(result.getPeriodStart()).isEqualTo(PERIOD_START);
        assertThat(result.getPeriodEnd()).isEqualTo(PERIOD_END);
        assertThat(result.getAttemptNo()).isEqualTo(1);
        assertThat(result.getBasedOnCount()).isEqualTo(10);
        assertThat(result.getExcludedEntryCount()).isEqualTo(3);
        assertThat(result.getSelectedEntryIds()).containsExactlyElementsOf(ENTRY_IDS);
        assertThat(result.getContent()).isNull();
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("start 시 memberId가 null이면 만들 수 없다.")
    void start_withNullMemberId_throws() {
        assertThatThrownBy(() -> MetaAnalysisResult.start(null, PERIOD_START, PERIOD_END, 1, 10, 0, ENTRY_IDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start 시 기간이 없거나 시작일이 종료일보다 늦으면 만들 수 없다.")
    void start_withInvalidPeriod_throws() {
        assertThatThrownBy(() -> MetaAnalysisResult.start(1L, null, PERIOD_END, 1, 10, 0, ENTRY_IDS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetaAnalysisResult.start(1L, PERIOD_END, PERIOD_START, 1, 10, 0, ENTRY_IDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start 시 attemptNo가 null이거나 1보다 작으면 만들 수 없다.")
    void start_withInvalidAttemptNo_throws() {
        assertThatThrownBy(() -> MetaAnalysisResult.start(1L, PERIOD_START, PERIOD_END, null, 10, 0, ENTRY_IDS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetaAnalysisResult.start(1L, PERIOD_START, PERIOD_END, 0, 10, 0, ENTRY_IDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start 시 개수가 음수이면 만들 수 없다.")
    void start_withNegativeCounts_throws() {
        assertThatThrownBy(() -> MetaAnalysisResult.start(1L, PERIOD_START, PERIOD_END, 1, -1, 0, ENTRY_IDS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetaAnalysisResult.start(1L, PERIOD_START, PERIOD_END, 1, 10, -1, ENTRY_IDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start 시 선택된 일기 목록이 없거나 비어 있으면 만들 수 없다.")
    void start_withMissingSelectedEntryIds_throws() {
        assertThatThrownBy(() -> MetaAnalysisResult.start(1L, PERIOD_START, PERIOD_END, 1, 10, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetaAnalysisResult.start(1L, PERIOD_START, PERIOD_END, 1, 10, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("처리 중인 결과를 complete하면 SUCCESS가 되고 내용이 저장된다.")
    void complete_processing_setsSuccessAndContent() {
        MetaAnalysisResult result = processing();
        MetaAnalysisContent content = content("겉모습 요약");

        result.complete(content);

        assertThat(result.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.SUCCESS);
        assertThat(result.getContent()).isSameAs(content);
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("complete 시 내용이 null이면 예외가 나고 상태는 PROCESSING으로 유지된다.")
    void complete_withNullContent_throwsAndKeepsProcessing() {
        MetaAnalysisResult result = processing();

        assertThatThrownBy(() -> result.complete(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(result.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.PROCESSING);
        assertThat(result.getContent()).isNull();
    }

    @Test
    @DisplayName("처리 중인 결과를 markFailed하면 FAILED가 되고 사유가 저장되며 내용은 비어있다.")
    void markFailed_processing_setsFailedAndReason() {
        MetaAnalysisResult result = processing();

        result.markFailed("GPT 호출 실패");

        assertThat(result.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("GPT 호출 실패");
        assertThat(result.getContent()).isNull();
    }

    @Test
    @DisplayName("markFailed 시 사유가 비어 있으면 예외가 나고 상태는 PROCESSING으로 유지된다.")
    void markFailed_withBlankReason_throwsAndKeepsProcessing() {
        MetaAnalysisResult result = processing();

        assertThatThrownBy(() -> result.markFailed(" "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(result.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.PROCESSING);
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("이미 성공한 결과는 다시 complete하거나 markFailed할 수 없다(종결 상태).")
    void transition_fromSuccess_throwsIllegalState() {
        MetaAnalysisResult result = processing();
        MetaAnalysisContent original = content("처음 요약");
        result.complete(original);

        assertThatThrownBy(() -> result.complete(content("다른 요약")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> result.markFailed("실패"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(result.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.SUCCESS);
        assertThat(result.getContent()).isSameAs(original);
    }

    @Test
    @DisplayName("이미 실패한 결과는 다시 complete하거나 markFailed할 수 없다(종결 상태, 늦게 도착한 결과를 버린다).")
    void transition_fromFailed_throwsIllegalState() {
        MetaAnalysisResult result = processing();
        result.markFailed("처음 실패 사유");

        assertThatThrownBy(() -> result.complete(content("늦게 온 결과")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> result.markFailed("다른 사유"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(result.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("처음 실패 사유");
        assertThat(result.getContent()).isNull();
    }
}
