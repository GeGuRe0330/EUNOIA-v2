package com.eunoia.journal.presentation.dto;

import com.eunoia.journal.application.dto.EmotionEntryInfo;

import java.time.LocalDate;

public record EmotionEntryResponse(
        Long id,
        Long memberId,
        String content,
        LocalDate entryDate
) {
    public static EmotionEntryResponse from(EmotionEntryInfo info) {
        return new EmotionEntryResponse(info.id(),  info.memberId(), info.content(), info.entryDate());
    }
}
