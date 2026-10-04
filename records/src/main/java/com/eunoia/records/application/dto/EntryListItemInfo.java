package com.eunoia.records.application.dto;

import java.time.LocalDate;

public record EntryListItemInfo(Long id, LocalDate entryDate, String content, String emotionDetected) {
}
