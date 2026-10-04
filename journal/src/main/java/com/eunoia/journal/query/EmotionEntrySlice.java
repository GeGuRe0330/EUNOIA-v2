package com.eunoia.journal.query;

import java.util.List;

public record EmotionEntrySlice(List<EmotionEntryListItem> items, boolean hasNext) {
}
