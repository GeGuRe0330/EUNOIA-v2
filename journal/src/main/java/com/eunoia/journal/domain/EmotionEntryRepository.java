package com.eunoia.journal.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmotionEntryRepository {
    EmotionEntry save(EmotionEntry entry);

    Optional<EmotionEntry> findById(Long id);

    List<EmotionEntry> findByIdInAndMemberId(List<Long> entryIds, Long memberId);

    boolean existsByIdAndMemberId(Long entryId, Long memberId);

    Slice<EmotionEntry> findSliceByMemberIdAndPeriod(Long memberId, LocalDate from, LocalDate to, Pageable pageable);

    long countByMemberIdAndPeriod(Long memberId, LocalDate from, LocalDate to);

    List<EntryDateCount> countDailyByMemberIdAndPeriod(Long memberId, LocalDate from, LocalDate to);
}
