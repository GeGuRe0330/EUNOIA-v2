package com.eunoia.insight.application.dto;

import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MetaAnalysisHistoryItem(
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer basedOnCount,
        Integer excludedEntryCount,
        MetaAnalysisContent content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MetaAnalysisHistoryItem from(MetaAnalysisResult result) {
        return new MetaAnalysisHistoryItem(
                result.getPeriodStart(), result.getPeriodEnd(), result.getBasedOnCount(),
                result.getExcludedEntryCount(), result.getContent(), result.getCreatedAt(),
                result.getUpdatedAt()
        );
    }
}
