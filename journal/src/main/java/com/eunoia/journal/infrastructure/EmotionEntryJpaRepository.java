package com.eunoia.journal.infrastructure;

import com.eunoia.journal.domain.EmotionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmotionEntryJpaRepository extends JpaRepository<EmotionEntry, Long> {
}
