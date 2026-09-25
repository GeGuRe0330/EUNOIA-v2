package com.eunoia.journal.query;

import java.util.List;

public interface EmotionEntryQueryApi {
    List<EmotionEntryContent> findContentsByEntryIds(Long memberId, List<Long> entryIds);
}
