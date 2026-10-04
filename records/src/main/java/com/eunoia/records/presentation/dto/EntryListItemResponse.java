package com.eunoia.records.presentation.dto;

import com.eunoia.records.application.dto.EntryListItemInfo;

import java.time.LocalDate;

public record EntryListItemResponse(Long id, LocalDate entryDate, String content, String emotionDetected) {
    public static EntryListItemResponse from(EntryListItemInfo info) {
        return new  EntryListItemResponse(info.id(),  info.entryDate(), info.content(), info.emotionDetected());
    }
}
