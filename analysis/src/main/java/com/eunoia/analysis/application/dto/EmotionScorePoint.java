package com.eunoia.analysis.application.dto;

import com.eunoia.analysis.domain.EmotionAnalysis;

import java.time.LocalDate;

public record EmotionScorePoint(
        Long entryId,
        LocalDate entryDate,
        Double emotionScore
) {
    public static EmotionScorePoint from(EmotionAnalysis analysis) {
        return new EmotionScorePoint(
                analysis.getEntryId(),
                analysis.getEntryDate(),
                analysis.getEmotionScore()
        );
    }
}
