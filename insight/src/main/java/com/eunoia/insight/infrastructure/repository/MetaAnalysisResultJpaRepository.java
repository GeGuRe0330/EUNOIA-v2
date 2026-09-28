package com.eunoia.insight.infrastructure.repository;

import com.eunoia.insight.domain.MetaAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MetaAnalysisResultJpaRepository extends JpaRepository<MetaAnalysisResult,Long> {
    Optional<MetaAnalysisResult> findTopByMemberIdOrderByPeriodEndDesc(Long memberId);

    Optional<MetaAnalysisResult> findByMemberIdAndPeriodEnd(Long memberId, LocalDate periodEnd);

    List<MetaAnalysisResult> findAllByMemberIdOrderByPeriodEndDesc(Long memberId);
}
