package com.eunoia.journal.presentation.dto;

import com.eunoia.journal.application.dto.WriteEmotionEntryCommand;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record EmotionEntryWriteRequest(
        @NotBlank String content,
        LocalDate entryDate
) {
    public WriteEmotionEntryCommand toCommand(Long memberId) {
        return new WriteEmotionEntryCommand(memberId, content, entryDate);
    }
}
