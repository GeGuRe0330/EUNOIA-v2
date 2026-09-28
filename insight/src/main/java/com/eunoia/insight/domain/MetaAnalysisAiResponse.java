package com.eunoia.insight.domain;

import java.util.List;

public record MetaAnalysisAiResponse(
        MetaAnalysisContent.Outer outer,
        MetaAnalysisContent.Inner inner,
        ClarityNarrative clarity
) {
    public record ClarityNarrative(
            List<String> clarityReasons,
            List<String> notVisibleYet,
            List<String> nextActions
    ) {}
}
