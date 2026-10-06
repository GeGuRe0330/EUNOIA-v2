package com.eunoia.insight.application.dto;

import com.eunoia.insight.application.MetaAnalysisCandidateSelector;
import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisGenerationStatus;
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
        LocalDateTime updatedAt,
        MetaAnalysisGenerationStatus generationStatus,
        String generationReason
) {
    private static final String GENERATION_FAILURE_REASON = "메타분석 생성에 실패했어요.";

    // latestSuccess: 가장 최근 완성된 결과(없으면 null) / todayAttempt: 오늘의 가장 최근 시도(상태 무관, 없으면 null)
    public static MetaAnalysisInfo of(MetaAnalysisStatus status, LocalDate periodStart, LocalDate periodEnd,
                                      int currentCount, MetaAnalysisResult latestSuccess, MetaAnalysisResult todayAttempt) {
        MetaAnalysisGenerationStatus generationStatus = null;
        String generationReason = null;
        if (todayAttempt != null && todayAttempt.getGenerationStatus() == MetaAnalysisGenerationStatus.PROCESSING) {
            generationStatus = MetaAnalysisGenerationStatus.PROCESSING;
        } else if (todayAttempt != null && todayAttempt.getGenerationStatus() == MetaAnalysisGenerationStatus.FAILED) {
            generationStatus = MetaAnalysisGenerationStatus.FAILED;
            generationReason = GENERATION_FAILURE_REASON;
        }

        if (latestSuccess == null) {
            return new MetaAnalysisInfo(status, periodStart, periodEnd,
                    MetaAnalysisCandidateSelector.MAX_CANDIDATES, currentCount, null, null, null,
                    generationStatus, generationReason);
        }

        return new MetaAnalysisInfo(status, periodStart, periodEnd,
                MetaAnalysisCandidateSelector.MAX_CANDIDATES, currentCount,
                latestSuccess.getContent(), latestSuccess.getCreatedAt(), latestSuccess.getUpdatedAt(),
                generationStatus, generationReason);
    }
}
