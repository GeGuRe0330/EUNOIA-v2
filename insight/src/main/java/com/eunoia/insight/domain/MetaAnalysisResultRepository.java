package com.eunoia.insight.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MetaAnalysisResultRepository {
    MetaAnalysisResult save(MetaAnalysisResult result);

    Optional<MetaAnalysisResult> findLatestByMemberId(Long memberId);

    Optional<MetaAnalysisResult> findByMemberIdAndPeriodEnd(Long memberId, LocalDate periodEnd);

    List<MetaAnalysisResult> findAllByMemberIdOrderByPeriodEndDesc(Long memberId);
}
