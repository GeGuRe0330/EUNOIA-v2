package com.eunoia.insight.application;

import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MetaAnalysisRegenerationGuardTest {

    private final MetaAnalysisRegenerationGuard guard = new MetaAnalysisRegenerationGuard();

    @Test
    @DisplayName("직전 결과가 없으면 재생성을 막지 않는다.")
    void isUnchanged_withNoPrevious_returnsFalse() {
        boolean result = guard.isUnchanged(List.of(1L, 2L), 0, Optional.empty());

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("entryId 집합이 순서만 다르고 동일하며 제외 건수도 같으면 변경 없음으로 판단한다.")
    void isUnchanged_withSameIdsDifferentOrderAndSameExcludedCount_returnsTrue() {
        MetaAnalysisResult previous = resultWithEvidence(List.of(1L, 2L, 3L), 2);

        boolean result = guard.isUnchanged(List.of(3L, 1L, 2L), 2, Optional.of(previous));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("entryId가 하나라도 다르면 변경된 것으로 판단한다.")
    void isUnchanged_withDifferentEntryId_returnsFalse() {
        MetaAnalysisResult previous = resultWithEvidence(List.of(1L, 2L, 3L), 2);

        boolean result = guard.isUnchanged(List.of(1L, 2L, 4L), 2, Optional.of(previous));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("entryId 집합은 같아도 제외 건수가 다르면 변경된 것으로 판단한다.")
    void isUnchanged_withSameIdsButDifferentExcludedCount_returnsFalse() {
        MetaAnalysisResult previous = resultWithEvidence(List.of(1L, 2L, 3L), 2);

        boolean result = guard.isUnchanged(List.of(1L, 2L, 3L), 5, Optional.of(previous));

        assertThat(result).isFalse();
    }

    private MetaAnalysisResult resultWithEvidence(List<Long> entryIds, int excludedEntryCount) {
        List<MetaAnalysisContent.RepresentativeEntry> evidence = entryIds.stream()
                .map(id -> new MetaAnalysisContent.RepresentativeEntry(id, "이유"))
                .toList();

        MetaAnalysisContent content = new MetaAnalysisContent(
                new MetaAnalysisContent.Outer("요약", List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new MetaAnalysisContent.Inner("요약", List.of(), List.of(), List.of(), List.of(), "", ""),
                new MetaAnalysisContent.Clarity(80, List.of(), List.of(), List.of()),
                evidence);

        return MetaAnalysisResult.create(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 30),
                entryIds.size(), excludedEntryCount, content);
    }
}
