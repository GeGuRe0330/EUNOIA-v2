package com.eunoia.analysis.presentation.dto;

import com.eunoia.analysis.application.dto.EmotionScorePoint;

import java.time.LocalDate;

public record EmotionScorePointResponse(
        Long entryId,
        LocalDate entryDate,
        Double emotionScore
) {
    public static EmotionScorePointResponse from(EmotionScorePoint point) {
        return new EmotionScorePointResponse(
                point.entryId(),
                point.entryDate(),
                point.emotionScore()
        );
    }
}
