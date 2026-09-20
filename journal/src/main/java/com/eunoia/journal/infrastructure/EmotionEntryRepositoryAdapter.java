package com.eunoia.journal.infrastructure;

import com.eunoia.journal.domain.EmotionEntry;
import com.eunoia.journal.domain.EmotionEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EmotionEntryRepositoryAdapter implements EmotionEntryRepository {

    private final EmotionEntryJpaRepository jpaRepository;

    @Override
    public EmotionEntry save(EmotionEntry entry) {
        return jpaRepository.save(entry);
    }
}
