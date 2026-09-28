package com.eunoia.journal.application.dto;

import java.time.LocalDate;

public record WriteEmotionEntryCommand(
        Long memberId,
        String content,
        LocalDate entryDate
) {
}
