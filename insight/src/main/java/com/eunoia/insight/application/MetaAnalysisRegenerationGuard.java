package com.eunoia.insight.application;

import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MetaAnalysisRegenerationGuard {
    public boolean isUnchanged(List<Long> newEntryIds, Optional<MetaAnalysisResult> previous) {
        return previous
                .map(result -> extractEntryIds(result).equals(Set.copyOf(newEntryIds)))
                .orElse(false);
    }

    private Set<Long> extractEntryIds(MetaAnalysisResult result) {
        return result.getContent().evidence().stream()
                .map(MetaAnalysisContent.RepresentativeEntry::entryId)
                .collect(Collectors.toSet());
    }
}
