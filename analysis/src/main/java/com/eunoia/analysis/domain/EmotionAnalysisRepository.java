package com.eunoia.analysis.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmotionAnalysisRepository {
    EmotionAnalysis save(EmotionAnalysis analysis);

    Optional<EmotionAnalysis> findByEntryId(Long entryId);

    List<EmotionAnalysis> findByMemberIdAndEntryDateBetweenAndStatusOrderByEntryDateAscEntryIdAsc
            (Long memberId, LocalDate startDate, LocalDate endDate, AnalysisStatus status);

    Optional<EmotionAnalysis> findTopByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<EmotionAnalysis> findTop7ByMemberIdAndStatusOrderByEntryDateDesc(Long memberId, AnalysisStatus status);
}
