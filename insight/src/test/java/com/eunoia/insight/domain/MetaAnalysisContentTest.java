package com.eunoia.insight.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetaAnalysisContentTest {

    @Test
    @DisplayName("Outer의 목록 필드가 null이면 빈 목록으로 받아들인다(GPT가 빈 배열 대신 null을 주는 경우).")
    void outer_withNullLists_becomesEmptyLists() {
        MetaAnalysisContent.Outer outer = new MetaAnalysisContent.Outer("요약", null, null, null, null, null, null);

        assertThat(outer.summary()).isEqualTo("요약");
        assertThat(outer.keywords()).isEmpty();
        assertThat(outer.triggers()).isEmpty();
        assertThat(outer.emotionFlows()).isEmpty();
        assertThat(outer.copingPatterns()).isEmpty();
        assertThat(outer.strengthSignals()).isEmpty();
        assertThat(outer.sensitivePoints()).isEmpty();
    }

    @Test
    @DisplayName("Inner와 Clarity의 목록 필드가 null이어도 빈 목록으로 받아들인다.")
    void innerAndClarity_withNullLists_becomeEmptyLists() {
        MetaAnalysisContent.Inner inner = new MetaAnalysisContent.Inner("내면", null, null, null, null, null, null);
        MetaAnalysisContent.Clarity clarity = new MetaAnalysisContent.Clarity(80, null, null, null);

        assertThat(inner.keywords()).isEmpty();
        assertThat(inner.coreValues()).isEmpty();
        assertThat(inner.needs()).isEmpty();
        assertThat(inner.innerMotivations()).isEmpty();
        assertThat(inner.outerInnerGap()).isNull(); // 문자열은 null을 그대로 둔다
        assertThat(clarity.clarityScore()).isEqualTo(80);
        assertThat(clarity.clarityReasons()).isEmpty();
        assertThat(clarity.notVisibleYet()).isEmpty();
        assertThat(clarity.nextActions()).isEmpty();
    }

    @Test
    @DisplayName("evidence가 null이어도 빈 목록으로 받아들이고, 값이 있으면 불변 복사본으로 보관한다.")
    void content_evidence_nullBecomesEmpty_andValuesAreCopiedImmutably() {
        MetaAnalysisContent.Outer outer = new MetaAnalysisContent.Outer("요약", List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        MetaAnalysisContent.Inner inner = new MetaAnalysisContent.Inner("내면", List.of(), List.of(), List.of(), List.of(), "", "");
        MetaAnalysisContent.Clarity clarity = new MetaAnalysisContent.Clarity(80, List.of(), List.of(), List.of());

        assertThat(new MetaAnalysisContent(outer, inner, clarity, null).evidence()).isEmpty();

        List<MetaAnalysisContent.RepresentativeEntry> source = new java.util.ArrayList<>(
                List.of(new MetaAnalysisContent.RepresentativeEntry(1L, LocalDate.of(2026, 10, 6), "이유")));
        MetaAnalysisContent content = new MetaAnalysisContent(outer, inner, clarity, source);
        source.clear();

        assertThat(content.evidence()).hasSize(1);
        assertThatThrownBy(() -> content.evidence().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("GPT 응답 JSON에서 목록 필드가 null이거나 빠져 있어도 역직렬화에 성공하고 빈 목록이 된다(실제 실패 사례 재현).")
    void aiResponseJson_withNullOrMissingLists_deserializesToEmptyLists() throws Exception {
        String json = """
                {
                  "outer": {"summary": "겉모습", "keywords": null, "triggers": ["야근"]},
                  "inner": {"summary": "내면"},
                  "clarity": {"clarityReasons": null, "nextActions": ["다음 제안"]}
                }
                """;

        MetaAnalysisAiResponse response = new ObjectMapper().readValue(json, MetaAnalysisAiResponse.class);

        assertThat(response.outer().summary()).isEqualTo("겉모습");
        assertThat(response.outer().keywords()).isEmpty();         // null로 옴
        assertThat(response.outer().triggers()).containsExactly("야근");
        assertThat(response.outer().emotionFlows()).isEmpty();      // 필드가 빠져 있음
        assertThat(response.inner().coreValues()).isEmpty();
        // ClarityNarrative는 GPT 응답용 중간 레코드라 null을 그대로 들고 있고, 워커가 Clarity로 옮길 때 빈 목록이 된다
        MetaAnalysisContent.Clarity clarity = new MetaAnalysisContent.Clarity(80,
                response.clarity().clarityReasons(), response.clarity().notVisibleYet(), response.clarity().nextActions());
        assertThat(clarity.clarityReasons()).isEmpty();      // null로 옴
        assertThat(clarity.notVisibleYet()).isEmpty();        // 필드가 빠져 있음
        assertThat(clarity.nextActions()).containsExactly("다음 제안");
    }
}
