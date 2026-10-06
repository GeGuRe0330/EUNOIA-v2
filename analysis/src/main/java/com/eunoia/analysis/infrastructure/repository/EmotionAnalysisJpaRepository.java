package com.eunoia.analysis.infrastructure.repository;

import com.eunoia.analysis.domain.AnalysisStatus;
import com.eunoia.analysis.domain.EmotionAnalysis;
import com.eunoia.analysis.domain.EntryDateAverageScore;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmotionAnalysisJpaRepository extends JpaRepository<EmotionAnalysis, Long> {
    Optional<EmotionAnalysis> findByEntryId(Long entryId);

    List<EmotionAnalysis> findByMemberIdAndEntryDateBetweenAndStatusOrderByEntryDateAscEntryIdAsc
            (Long memberId, LocalDate startDate, LocalDate endDate, AnalysisStatus status);

    Optional<EmotionAnalysis> findTopByMemberIdAndStatusInOrderByEntryDateDescEntryIdDesc(Long memberId, List<AnalysisStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from EmotionAnalysis a where a.entryId = :entryId")
    Optional<EmotionAnalysis> findByEntryIdForUpdate(Long entryId);

    @Modifying
    @Query("""
            update EmotionAnalysis a
                set a.status = :failedStatus, a.failureReason = :failureReason, a.updatedAt = :now
            where a.status = :processingStatus
                and a.deletedAt is null
                and a.createdAt < :threshold
            """)
    int failProcessingCreatedBefore(AnalysisStatus processingStatus, AnalysisStatus failedStatus,
                                    String failureReason, LocalDateTime threshold, LocalDateTime now);

    List<EmotionAnalysis> findTop7ByMemberIdAndStatusOrderByEntryDateDescEntryIdDesc(Long memberId, AnalysisStatus status);

    List<EmotionAnalysis> findByMemberIdAndEntryIdInAndStatus(Long memberId, List<Long> entryIds, AnalysisStatus status);

    @Query("""
            select new com.eunoia.analysis.domain.EntryDateAverageScore(a.entryDate, avg(a.emotionScore))
            from EmotionAnalysis a
            where a.memberId = :memberId
                  and a.status = :status
                  and a.entryDate between  :from and :to
            group by a.entryDate
            order by a.entryDate
            """)
    List<EntryDateAverageScore> averageScoreDailyByMemberIdAndPeriod(Long memberId, AnalysisStatus status, LocalDate from, LocalDate to);
}
