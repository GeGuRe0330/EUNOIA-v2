package com.eunoia.analysis.infrastructure.repository;

import com.eunoia.analysis.domain.AnalysisStatus;
import com.eunoia.analysis.domain.EmotionAnalysis;
import com.eunoia.analysis.domain.EmotionAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmotionAnalysisRepositoryAdapter implements EmotionAnalysisRepository {

    private final EmotionAnalysisJpaRepository jpaRepository;

    @Override
    public EmotionAnalysis save(EmotionAnalysis analysis) {
        return jpaRepository.save(analysis);
    }

    @Override
    public Optional<EmotionAnalysis> findByEntryId(Long entryId) {
        return jpaRepository.findByEntryId(entryId);
    }

    @Override
    public List<EmotionAnalysis> findByMemberIdAndEntryDateBetweenAndStatus(
            Long memberId, LocalDate startDate, LocalDate endDate, AnalysisStatus status) {
        return jpaRepository.findByMemberIdAndEntryDateBetweenAndStatus(memberId, startDate, endDate, status);
    }
}
