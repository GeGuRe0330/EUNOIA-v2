package com.eunoia.analysis.presentation.dto;

import com.eunoia.analysis.application.dto.EmotionAnalysisInfo;

import java.util.List;

public record EmotionAnalysisResponse(
        Long entryId,
        Long memberId,
        String emotionDetected,
        String keywords,
        String insightSummary,
        String flowHint,
        String emotionSummary,
        Double emotionScore,
        List<String> warmMessages
) {
    public static EmotionAnalysisResponse from(EmotionAnalysisInfo info) {
        return new EmotionAnalysisResponse(
                info.entryId(),
                info.memberId(),
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
