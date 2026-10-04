package com.eunoia.records.application.dto;

import java.time.LocalDate;

public record CalendarDayInfo(LocalDate date, long entryCount, Double averageScore) {
}
