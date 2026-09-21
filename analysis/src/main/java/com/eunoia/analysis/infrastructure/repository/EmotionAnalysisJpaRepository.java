package com.eunoia.analysis.infrastructure.repository;

import com.eunoia.analysis.domain.EmotionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmotionAnalysisJpaRepository extends JpaRepository<EmotionAnalysis, Long> {
    Optional<EmotionAnalysis> findByEntryId(Long entryId);
}
