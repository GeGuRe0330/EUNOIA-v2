package com.eunoia.insight.application;

import com.eunoia.insight.application.dto.MetaAnalysisGenerationRequested;
import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MetaAnalysisGenerationClaimer {

    private final MetaAnalysisResultRepository metaAnalysisResultRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MetaAnalysisResult claim(Long memberId, LocalDate periodStart, LocalDate periodEnd, int attemptNo,
                                    int basedOnCount, int excludedEntryCount, List<Long> selectedEntryIds) {
        MetaAnalysisResult started = metaAnalysisResultRepository.save(MetaAnalysisResult.start(
                memberId, periodStart, periodEnd, attemptNo, basedOnCount, excludedEntryCount, selectedEntryIds
        ));

        eventPublisher.publishEvent(new MetaAnalysisGenerationRequested(started.getId()));

        return started;
    }
}
