package com.eunoia.insight.domain;

import java.util.List;

public record MetaAnalysisContent(
        Outer outer,
        Inner inner,
        Clarity clarity,
        List<RepresentativeEntry> evidence
) {

    public record Outer(
            String summary,
            List<String> keywords,
            List<String> triggers,
            List<String> emotionFlows,
            List<String> copingPatterns,
            List<String> strengthSignals,
            List<String> sensitivePoints
    ) {}

    public record Inner(
            String summary,
            List<String> keywords,
            List<String> coreValues,
            List<String> needs,
            List<String> innerMotivations,
            String outerInnerGap,
            String gapExplanation
    ) {}

    public record Clarity(
            Integer clarityScore,
            List<String> clarityReasons,
            List<String> notVisibleYet,
            List<String> nextActions
    ) {}

    public record RepresentativeEntry(
            Long entryId,
            String whySelected
    ) {}
}
