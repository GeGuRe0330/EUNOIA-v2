package com.eunoia.records.presentation.dto;

import com.eunoia.records.application.dto.EntryPageInfo;

import java.util.List;

public record EntryPageResponse(List<EntryListItemResponse> items, int page, int size, boolean hasNext) {
    public static EntryPageResponse from(EntryPageInfo info) {
        return new EntryPageResponse(
                info.items().stream().map(EntryListItemResponse::from).toList(),
                info.page(),
                info.size(),
                info.hasNext()
        );
    }
}
