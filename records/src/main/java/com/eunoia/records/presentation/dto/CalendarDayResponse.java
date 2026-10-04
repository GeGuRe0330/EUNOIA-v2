package com.eunoia.records.presentation.dto;

import com.eunoia.records.application.dto.CalendarDayInfo;

import java.time.LocalDate;

public record CalendarDayResponse(LocalDate date, long entryCount, Double averageScore) {
    public static CalendarDayResponse from(CalendarDayInfo info) {
        return new CalendarDayResponse(info.date(), info.entryCount(), info.averageScore());
    }
}
