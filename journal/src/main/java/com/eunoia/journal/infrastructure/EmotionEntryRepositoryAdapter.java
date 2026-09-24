package com.eunoia.journal.infrastructure;

import com.eunoia.journal.domain.EmotionEntry;
import com.eunoia.journal.domain.EmotionEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmotionEntryRepositoryAdapter implements EmotionEntryRepository {

    private final EmotionEntryJpaRepository jpaRepository;

    @Override
    public EmotionEntry save(EmotionEntry entry) {
        return jpaRepository.save(entry);
    }

    @Override
    public Optional<EmotionEntry> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<EmotionEntry> findByMemberIdOrderByEntryDateDesc(Long memberId) {
        return jpaRepository.findByMemberIdOrderByEntryDateDesc(memberId);
    }

    @Override
    public List<EmotionEntry> findByIdIn(List<Long> entryIds) {
        return jpaRepository.findAllById(entryIds);
    }
}
