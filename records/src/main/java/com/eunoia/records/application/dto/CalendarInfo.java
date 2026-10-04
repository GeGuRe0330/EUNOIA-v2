package com.eunoia.records.application.dto;

import java.time.YearMonth;
import java.util.List;

public record CalendarInfo(YearMonth yearMonth, List<CalendarDayInfo> days) {
}
