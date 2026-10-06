package com.eunoia.insight.infrastructure.repository;

import com.eunoia.insight.domain.MetaAnalysisGenerationStatus;
import com.eunoia.insight.domain.MetaAnalysisResult;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MetaAnalysisResultJpaRepository extends JpaRepository<MetaAnalysisResult,Long> {
    Optional<MetaAnalysisResult> findTopByMemberIdAndGenerationStatusOrderByPeriodEndDescAttemptNoDesc(
            Long memberId, MetaAnalysisGenerationStatus generationStatus
    );

    List<MetaAnalysisResult> findAllByMemberIdAndGenerationStatusOrderByPeriodEndDescAttemptNoDesc(
            Long memberId,  MetaAnalysisGenerationStatus generationStatus
    );

    Optional<MetaAnalysisResult> findTopByMemberIdAndPeriodEndOrderByAttemptNoDesc(
            Long memberId, LocalDate periodEnd
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MetaAnalysisResult r where r.id = :id")
    Optional<MetaAnalysisResult> findByIdForUpdate(Long id);

    @Modifying
    @Query("""
            update MetaAnalysisResult r
               set r.generationStatus = :failedStatus, r.failureReason = :failureReason, r.updatedAt = :now
             where r.generationStatus = :processingStatus
                   and r.createdAt < :threshold
            """)
    int failProcessingCreatedBefore(MetaAnalysisGenerationStatus processingStatus, MetaAnalysisGenerationStatus failedStatus,
                                    String failureReason, LocalDateTime threshold, LocalDateTime now);
}
