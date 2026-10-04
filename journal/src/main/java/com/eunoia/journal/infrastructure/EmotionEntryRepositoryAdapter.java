package com.eunoia.journal.infrastructure;

import com.eunoia.journal.domain.EmotionEntry;
import com.eunoia.journal.domain.EmotionEntryRepository;
import com.eunoia.journal.domain.EntryDateCount;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
    public List<EmotionEntry> findByIdInAndMemberId(List<Long> entryIds,  Long memberId) {
        return jpaRepository.findByIdInAndMemberId(entryIds, memberId);
    }

    @Override
    public boolean existsByIdAndMemberId(Long entryId, Long memberId) {
        return jpaRepository.existsByIdAndMemberId(entryId, memberId);
    }

    @Override
    public Slice<EmotionEntry> findSliceByMemberIdAndPeriod(Long memberId, LocalDate from, LocalDate to, Pageable pageable) {
        return jpaRepository.findSliceByMemberIdAndPeriod(memberId, from, to, pageable);
    }

    @Override
    public long countByMemberIdAndPeriod(Long memberId, LocalDate from, LocalDate to) {
        return jpaRepository.countByMemberIdAndPeriod(memberId, from, to);
    }

    @Override
    public List<EntryDateCount> countDailyByMemberIdAndPeriod(Long memberId, LocalDate from, LocalDate to) {
        return jpaRepository.countDailyByMemberIdAndPeriod(memberId, from, to);
    }
}
