package com.eunoia.journal.application.dto;

import com.eunoia.journal.domain.EmotionEntry;

import java.time.LocalDate;

public record EmotionEntryInfo(
        Long id,
        Long memberId,
        String content,
        LocalDate entryDate
) {
    public static EmotionEntryInfo from(EmotionEntry entry) {
        return new EmotionEntryInfo(
                entry.getId(),
                entry.getMemberId(),
                entry.getContent(),
                entry.getEntryDate()
        );
    }
}
