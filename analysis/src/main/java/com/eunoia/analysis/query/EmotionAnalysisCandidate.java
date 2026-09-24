package com.eunoia.analysis.query;

import java.time.LocalDate;

public record EmotionAnalysisCandidate(
        Long entryId,
        LocalDate entryDate,
        Integer entryClarityScore,
        String entryClarityReason
) {
}
