package com.eunoia.journal.query;

import java.time.LocalDate;

public record DailyEntryCount(LocalDate date, long entryCount) {
}
