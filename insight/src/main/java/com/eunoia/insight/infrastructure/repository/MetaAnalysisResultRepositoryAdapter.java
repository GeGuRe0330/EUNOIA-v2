package com.eunoia.insight.infrastructure.repository;

import com.eunoia.insight.domain.MetaAnalysisGenerationStatus;
import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MetaAnalysisResultRepositoryAdapter implements MetaAnalysisResultRepository {

    private final MetaAnalysisResultJpaRepository jpaRepository;

    @Override
    public MetaAnalysisResult save(MetaAnalysisResult result) {
        return jpaRepository.save(result);
    }

    @Override
    public Optional<MetaAnalysisResult> findLatestSuccessByMemberId(Long memberId) {
        return jpaRepository.findTopByMemberIdAndGenerationStatusOrderByPeriodEndDescAttemptNoDesc(
                memberId, MetaAnalysisGenerationStatus.SUCCESS
        );
    }

    @Override
    public List<MetaAnalysisResult> findAllSuccessByMemberId(Long memberId) {
        return jpaRepository.findAllByMemberIdAndGenerationStatusOrderByPeriodEndDescAttemptNoDesc(
                memberId,  MetaAnalysisGenerationStatus.SUCCESS
        );
    }

    @Override
    public Optional<MetaAnalysisResult> findLatestAttemptOfDay(Long memberId,  LocalDate periodEnd) {
        return jpaRepository.findTopByMemberIdAndPeriodEndOrderByAttemptNoDesc(memberId, periodEnd);
    }

    @Override
    public Optional<MetaAnalysisResult> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<MetaAnalysisResult> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id);
    }

    @Override
    public int failProcessingCreatedBefore(LocalDateTime threshold, String failureReason) {
        return jpaRepository.failProcessingCreatedBefore(
                MetaAnalysisGenerationStatus.PROCESSING, MetaAnalysisGenerationStatus.FAILED,
                failureReason, threshold, LocalDateTime.now()
        );
    }
}
