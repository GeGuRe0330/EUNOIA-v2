package com.eunoia.insight.application.dto;

import com.eunoia.insight.application.MetaAnalysisCandidateSelector;
import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MetaAnalysisInfo(
        MetaAnalysisStatus status,
        LocalDate periodStart,
        LocalDate periodEnd,
        int requiredCount,
        int currentCount,
        MetaAnalysisContent content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MetaAnalysisInfo of(MetaAnalysisStatus status, LocalDate periodStart, LocalDate periodEnd,
                                      int currentCount, MetaAnalysisResult result) {
        if (result == null) {
            return new MetaAnalysisInfo(status, periodStart, periodEnd,
                    MetaAnalysisCandidateSelector.MAX_CANDIDATES, currentCount, null, null, null);
        }

        return new MetaAnalysisInfo(status, periodStart, periodEnd,
                MetaAnalysisCandidateSelector.MAX_CANDIDATES, currentCount,
                result.getContent(), result.getCreatedAt(), result.getUpdatedAt());
    }
}
