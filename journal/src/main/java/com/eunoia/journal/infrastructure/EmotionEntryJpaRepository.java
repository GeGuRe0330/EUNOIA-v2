package com.eunoia.journal.infrastructure;

import com.eunoia.journal.domain.EmotionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmotionEntryJpaRepository extends JpaRepository<EmotionEntry, Long> {
    List<EmotionEntry> findByMemberIdOrderByEntryDateDesc(Long memberId);

    List<EmotionEntry> findByIdInAndMemberId(List<Long> entryIds, Long memberId);
}
