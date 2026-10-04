package com.eunoia.journal.domain;

import java.time.LocalDate;

public record EntryDateCount(LocalDate entryDate, long count) {
}
