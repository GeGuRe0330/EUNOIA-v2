package com.eunoia.journal.event;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmotionEntryRecorded(
        Long entryId,
        Long memberId,
        String content,
        LocalDate entryDate,
        LocalDateTime recordedAt
) {
    public static EmotionEntryRecorded of(Long entryId, Long memberId, String content, LocalDate entryDate) {
        return new EmotionEntryRecorded(entryId, memberId, content, entryDate, LocalDateTime.now());
    }
}
