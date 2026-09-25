package com.eunoia.insight.application;

import com.eunoia.analysis.query.EmotionAnalysisCandidate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MetaAnalysisCandidateSelector {

    private static final int MIN_CLARITY_SCORE = 80;
    // Service에서도 필요한 상수라 public으로 선언
    public static final int MAX_CANDIDATES = 10;

    public MetaAnalysisSelection select(List<EmotionAnalysisCandidate> candidates) {
        List<EmotionAnalysisCandidate> qualified = candidates.stream()
                .filter(candidate -> candidate.entryClarityScore() >= MIN_CLARITY_SCORE)
                .toList();

        Map<LocalDate, EmotionAnalysisCandidate> bestPerDay = new LinkedHashMap<>();
        for (EmotionAnalysisCandidate candidate : qualified) {
            bestPerDay.merge(candidate.entryDate(), candidate, this::selectBetter);
        }

        List<EmotionAnalysisCandidate> selected = bestPerDay.values().stream()
                .sorted(Comparator.comparing(EmotionAnalysisCandidate::entryDate).reversed())
                .limit(MAX_CANDIDATES)
                .sorted(Comparator.comparing(EmotionAnalysisCandidate::entryDate))
                .toList();

        int excludedEntryCount = candidates.size() - selected.size();

        return new MetaAnalysisSelection(selected, excludedEntryCount);
    }

    private EmotionAnalysisCandidate selectBetter(EmotionAnalysisCandidate existing, EmotionAnalysisCandidate incoming) {
        if (incoming.entryClarityScore() > existing.entryClarityScore()) {
            return incoming;
        }
        if (incoming.entryClarityScore() < existing.entryClarityScore()) {
            return existing;
        }
        return incoming.entryId() < existing.entryId() ? incoming : existing;
    }
}
