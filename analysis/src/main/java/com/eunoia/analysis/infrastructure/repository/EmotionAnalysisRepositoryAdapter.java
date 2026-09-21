package com.eunoia.analysis.infrastructure.repository;

import com.eunoia.analysis.domain.EmotionAnalysis;
import com.eunoia.analysis.domain.EmotionAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
}
