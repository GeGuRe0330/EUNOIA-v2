package com.eunoia.insight.application;

import com.eunoia.analysis.query.EmotionAnalysisCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetaAnalysisCandidateSelectorTest {

    private MetaAnalysisCandidateSelector selector;

    @BeforeEach
    void setUp() {
        selector = new MetaAnalysisCandidateSelector();
    }

    @Test
    @DisplayName("entryClarityScore가 80 미만이면 후보에서 제외한다.")
    void select_withLowClarityScore_excludesCandidate() {
        List<EmotionAnalysisCandidate> candidates = List.of(
                candidate(1L, LocalDate.of(2026, 9, 1), 79),
                candidate(2L, LocalDate.of(2026, 9, 2), 80));

        MetaAnalysisSelection result = selector.select(candidates);

        assertThat(result.selected()).extracting(EmotionAnalysisCandidate::entryId).containsExactly(2L);
        assertThat(result.excludedEntryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 날짜에 여러 후보가 있으면 점수가 가장 높은 것만 선택한다.")
    void select_withMultipleCandidatesOnSameDate_picksHighestScore() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        List<EmotionAnalysisCandidate> candidates = List.of(
                candidate(1L, date, 80),
                candidate(2L, date, 95));

        MetaAnalysisSelection result = selector.select(candidates);

        assertThat(result.selected()).extracting(EmotionAnalysisCandidate::entryId).containsExactly(2L);
    }

    @Test
    @DisplayName("같은 날짜/같은 점수로 동점이면 entryId가 작은 쪽을 선택한다.")
    void select_withTiedScoreOnSameDate_picksSmallerEntryId() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        List<EmotionAnalysisCandidate> candidates = List.of(
                candidate(5L, date, 90),
                candidate(2L, date, 90));

        MetaAnalysisSelection result = selector.select(candidates);

        assertThat(result.selected()).extracting(EmotionAnalysisCandidate::entryId).containsExactly(2L);
    }

    @Test
    @DisplayName("조건을 만족하는 날짜가 10일보다 많으면 가장 최근 10일만 선택한다.")
    void select_withMoreThanTenQualifyingDays_picksMostRecentTen() {
        List<EmotionAnalysisCandidate> candidates = new ArrayList<>();
        for (int day = 1; day <= 12; day++) {
            candidates.add(candidate((long) day, LocalDate.of(2026, 9, day), 90));
        }

        MetaAnalysisSelection result = selector.select(candidates);

        assertThat(result.selected()).hasSize(10);
        assertThat(result.selected().get(0).entryDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(result.selected().get(9).entryDate()).isEqualTo(LocalDate.of(2026, 9, 12));
    }

    @Test
    @DisplayName("최종 선택 결과는 과거에서 최근 순으로 정렬된다.")
    void select_returnsSortedFromOldestToNewest() {
        List<EmotionAnalysisCandidate> candidates = List.of(
                candidate(1L, LocalDate.of(2026, 9, 3), 90),
                candidate(2L, LocalDate.of(2026, 9, 1), 90),
                candidate(3L, LocalDate.of(2026, 9, 2), 90));

        MetaAnalysisSelection result = selector.select(candidates);

        assertThat(result.selected()).extracting(EmotionAnalysisCandidate::entryDate)
                .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3));
    }

    @Test
    @DisplayName("전체 후보 수에서 실제 선택된 수를 뺀 값을 제외 건수로 계산한다.")
    void select_calculatesExcludedEntryCount() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        List<EmotionAnalysisCandidate> candidates = List.of(
                candidate(1L, date, 90),
                candidate(2L, date, 95),
                candidate(3L, LocalDate.of(2026, 9, 2), 70));

        MetaAnalysisSelection result = selector.select(candidates);

        assertThat(result.selected()).hasSize(1);
        assertThat(result.excludedEntryCount()).isEqualTo(2);
    }

    private EmotionAnalysisCandidate candidate(Long entryId, LocalDate entryDate, int score) {
        return new EmotionAnalysisCandidate(entryId, entryDate, score, "이유");
    }
}
