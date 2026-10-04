package com.eunoia.records.application.dto;

import java.util.List;

public record EntryPageInfo(List<EntryListItemInfo> items, int page, int size, boolean hasNext) {
}
