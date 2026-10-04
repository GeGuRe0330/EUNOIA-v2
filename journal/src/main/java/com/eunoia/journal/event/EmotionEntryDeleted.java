package com.eunoia.journal.event;

import java.time.LocalDateTime;

public record EmotionEntryDeleted(
        Long entryId,
        Long memberId,
        LocalDateTime deletedAt
) {
    public static EmotionEntryDeleted of(Long entryId, Long memberId, LocalDateTime deletedAt) {
        return new EmotionEntryDeleted(entryId, memberId, deletedAt);
    }
}
