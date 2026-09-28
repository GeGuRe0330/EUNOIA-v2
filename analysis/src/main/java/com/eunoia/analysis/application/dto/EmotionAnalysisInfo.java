package com.eunoia.analysis.application.dto;

import com.eunoia.analysis.domain.AnalysisStatus;
import com.eunoia.analysis.domain.EmotionAnalysis;

import java.util.List;

public record EmotionAnalysisInfo(
        Long entryId,
        Long memberId,
        AnalysisStatus status,
        String emotionDetected,
        String keywords,
        String insightSummary,
        String flowHint,
        String emotionSummary,
        Double emotionScore,
        List<String> warmMessages
) {
    public static EmotionAnalysisInfo from(EmotionAnalysis analysis) {
        return new EmotionAnalysisInfo(
                analysis.getEntryId(),
                analysis.getMemberId(),
                analysis.getStatus(),
                analysis.getEmotionDetected(),
                analysis.getKeywords(),
                analysis.getInsightSummary(),
                analysis.getFlowHint(),
                analysis.getEmotionSummary(),
                analysis.getEmotionScore(),
                analysis.getWarmMessages()
        );
    }
}
