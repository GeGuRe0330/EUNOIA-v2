package com.eunoia.journal.domain;

import java.util.List;
import java.util.Optional;

public interface EmotionEntryRepository {
    EmotionEntry save(EmotionEntry entry);

    Optional<EmotionEntry> findById(Long id);

    List<EmotionEntry> findByMemberIdOrderByEntryDateDesc(Long memberId );

    List<EmotionEntry> findByIdIn(List<Long> entryIds);
}
