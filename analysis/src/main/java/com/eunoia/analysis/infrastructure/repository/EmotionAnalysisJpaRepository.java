package com.eunoia.analysis.infrastructure.repository;

import com.eunoia.analysis.domain.AnalysisStatus;
import com.eunoia.analysis.domain.EmotionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmotionAnalysisJpaRepository extends JpaRepository<EmotionAnalysis, Long> {
    Optional<EmotionAnalysis> findByEntryId(Long entryId);

    List<EmotionAnalysis> findByMemberIdAndEntryDateBetweenAndStatus(Long memberId, LocalDate startDate,
                                                                     LocalDate endDate, AnalysisStatus status);
}
