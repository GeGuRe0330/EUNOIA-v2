package com.eunoia.analysis.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmotionAnalysisRepository {
    EmotionAnalysis save(EmotionAnalysis analysis);

    Optional<EmotionAnalysis> findByEntryId(Long entryId);

    List<EmotionAnalysis> findByMemberIdAndEntryDateBetweenAndStatusOrderByEntryDateAscEntryIdAsc
            (Long memberId, LocalDate startDate, LocalDate endDate, AnalysisStatus status);

    Optional<EmotionAnalysis> findTopByMemberIdAndStatusInOrderByEntryDateDescEntryIdDesc(Long memberId, List<AnalysisStatus> statuses);

    Optional<EmotionAnalysis> findByEntryIdForUpdate(Long entryId);

    int failProcessingCreatedBefore(LocalDateTime threshold, String failureReason);

    List<EmotionAnalysis> findTop7ByMemberIdAndStatusOrderByEntryDateDescEntryIdDesc(Long memberId, AnalysisStatus status);

    List<EmotionAnalysis> findByMemberIdAndEntryIdInAndStatus(Long memberId, List<Long> entryIds, AnalysisStatus status);

    List<EntryDateAverageScore> averageScoreDailyByMemberIdAndPeriod(Long memberId, AnalysisStatus status, LocalDate from, LocalDate to);
}
