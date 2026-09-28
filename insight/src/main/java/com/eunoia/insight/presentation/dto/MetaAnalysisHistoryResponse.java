package com.eunoia.insight.presentation.dto;

import com.eunoia.insight.application.dto.MetaAnalysisHistoryItem;
import com.eunoia.insight.domain.MetaAnalysisContent;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MetaAnalysisHistoryResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer basedOnCount,
        Integer excludedEntryCount,
        MetaAnalysisContent content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MetaAnalysisHistoryResponse from(MetaAnalysisHistoryItem item) {
        return new MetaAnalysisHistoryResponse(
                item.periodStart(), item.periodEnd(), item.basedOnCount(),
                item.excludedEntryCount(), item.content(), item.createdAt(), item.updatedAt()
        );
    }
}
