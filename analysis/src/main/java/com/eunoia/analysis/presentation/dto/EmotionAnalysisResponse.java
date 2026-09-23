package com.eunoia.analysis.presentation.dto;

import com.eunoia.analysis.application.dto.EmotionAnalysisInfo;
import com.eunoia.analysis.domain.AnalysisStatus;

import java.util.List;

public record EmotionAnalysisResponse(
        Long entryId,
        Long memberId,
        AnalysisStatus status,
        String reason,
        String emotionDetected,
        String keywords,
        String insightSummary,
        String flowHint,
        String emotionSummary,
        Double emotionScore,
        List<String> warmMessages
) {

    private static final String FAILURE_REASON = "잠시 후에 다시 시도해주세요.";

    public static EmotionAnalysisResponse from(EmotionAnalysisInfo info) {
        return new EmotionAnalysisResponse(
                info.entryId(),
                info.memberId(),
                info.status(),
                info.status() == AnalysisStatus.FAILED ? FAILURE_REASON : null,
                info.emotionDetected(),
                info.keywords(),
                info.insightSummary(),
                info.flowHint(),
                info.emotionSummary(),
                info.emotionScore(),
                info.warmMessages()
        );
    }
}
