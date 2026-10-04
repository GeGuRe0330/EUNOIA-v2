package com.eunoia.journal.query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmotionEntryQueryApi {
    Optional<String> findContent(Long memberId, Long entryId);

    List<EmotionEntryContent> findContentsByEntryIds(Long memberId, List<Long> entryIds);

    boolean existsEntry(Long memberId, Long entryId);

    EmotionEntrySlice findEntries(Long memberId, LocalDate from, LocalDate to, int page, int size);

    long countEntries(Long memberId, LocalDate from, LocalDate to);

    List<DailyEntryCount> countEntriesByDate(Long memberId, LocalDate from, LocalDate to);
}
