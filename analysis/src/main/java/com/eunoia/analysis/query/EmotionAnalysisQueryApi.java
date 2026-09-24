package com.eunoia.analysis.query;

import java.time.LocalDate;
import java.util.List;

public interface EmotionAnalysisQueryApi {
    List<EmotionAnalysisCandidate> findSuccessfulAnalyses(Long memberId, LocalDate startDate, LocalDate endDate);
}
