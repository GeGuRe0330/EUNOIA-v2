package com.eunoia.insight.presentation.dto;

import com.eunoia.insight.application.dto.MetaAnalysisInfo;
import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MetaAnalysisResponse(
        MetaAnalysisStatus status,
        LocalDate periodStart,
        LocalDate periodEnd,
        int requiredCount,
        int currentCount,
        MetaAnalysisContent content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MetaAnalysisResponse from(MetaAnalysisInfo info) {
        return new MetaAnalysisResponse(
                info.status(),
                info.periodStart(),
                info.periodEnd(),
                info.requiredCount(),
                info.currentCount(),
                info.content(),
                info.createdAt(),
                info.updatedAt()
        );
    }
}
