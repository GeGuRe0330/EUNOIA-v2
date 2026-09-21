package com.eunoia.analysis.domain;

import java.util.List;

public record EmotionAnalysisResult(
        String emotionDetected,
        String keywords,
        String insightSummary,
        String flowHint,
        String emotionSummary,
        Double emotionScore,
        Integer entryClarityScore,
        String entryClarityReason,
        List<String> warmMessages
) {
}
