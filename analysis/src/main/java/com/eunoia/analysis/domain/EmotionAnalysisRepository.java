package com.eunoia.analysis.domain;

import java.util.Optional;

public interface EmotionAnalysisRepository {
    EmotionAnalysis save(EmotionAnalysis analysis);

    Optional<EmotionAnalysis> findByEntryId(Long entryId);
}
