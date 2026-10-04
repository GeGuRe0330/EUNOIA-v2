package com.eunoia.journal.query;

import java.time.LocalDate;

public record EmotionEntryListItem(Long entryId, LocalDate entryDate, String content) {
}
