package com.eunoia.insight.infrastructure.repository;

import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
    public Optional<MetaAnalysisResult> findLatestByMemberId(Long memberId) {
        return jpaRepository.findTopByMemberIdOrderByPeriodEndDesc(memberId);
    }

    @Override
    public Optional<MetaAnalysisResult> findByMemberIdAndPeriodEnd(Long memberId, LocalDate periodEnd) {
        return jpaRepository.findByMemberIdAndPeriodEnd(memberId, periodEnd);
    }

    @Override
    public List<MetaAnalysisResult> findAllByMemberIdOrderByPeriodEndDesc(Long memberId) {
        return jpaRepository.findAllByMemberIdOrderByPeriodEndDesc(memberId);
    }
}
