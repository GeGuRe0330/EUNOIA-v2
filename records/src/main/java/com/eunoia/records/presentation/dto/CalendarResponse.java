package com.eunoia.records.presentation.dto;

import com.eunoia.records.application.dto.CalendarInfo;

import java.time.YearMonth;
import java.util.List;

public record CalendarResponse(YearMonth yearMonth, List<CalendarDayResponse> days) {
    public static CalendarResponse from(CalendarInfo info) {
        return new CalendarResponse(info.yearMonth(), info.days().stream().map(CalendarDayResponse::from).toList());
    }
}
