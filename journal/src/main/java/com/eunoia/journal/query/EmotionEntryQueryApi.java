package com.eunoia.journal.query;

import java.time.LocalDate;
import java.util.List;

public interface EmotionEntryQueryApi {
    List<EmotionEntryContent> findContentsByEntryIds(Long memberId, List<Long> entryIds);

    boolean existsEntry(Long memberId, Long entryId);

    EmotionEntrySlice findEntries(Long memberId, LocalDate from, LocalDate to, int page, int size);

    long countEntries(Long memberId, LocalDate from, LocalDate to);

    List<DailyEntryCount> countEntriesByDate(Long memberId, LocalDate from, LocalDate to);
}
