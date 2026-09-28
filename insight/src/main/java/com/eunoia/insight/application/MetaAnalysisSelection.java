package com.eunoia.insight.application;

import com.eunoia.analysis.query.EmotionAnalysisCandidate;

import java.util.List;

public record MetaAnalysisSelection(
        List<EmotionAnalysisCandidate> selected,
        int excludedEntryCount
) {
}
