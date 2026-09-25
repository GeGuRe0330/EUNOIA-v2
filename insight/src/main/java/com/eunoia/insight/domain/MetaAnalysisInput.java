package com.eunoia.insight.domain;

import java.util.List;

public record MetaAnalysisInput(
        List<String> entryContents,
        Integer excludedEntryCount,
        Integer clarityScore
) {
}
