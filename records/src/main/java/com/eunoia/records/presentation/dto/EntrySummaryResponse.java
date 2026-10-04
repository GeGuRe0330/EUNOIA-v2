package com.eunoia.records.presentation.dto;

import com.eunoia.records.application.dto.EntrySummaryInfo;

public record EntrySummaryResponse(long totalEntryCount, long monthEntryCount) {
    public static EntrySummaryResponse from(EntrySummaryInfo info) {
        return new EntrySummaryResponse(info.totalEntryCount(),  info.monthEntryCount());
    }
}
