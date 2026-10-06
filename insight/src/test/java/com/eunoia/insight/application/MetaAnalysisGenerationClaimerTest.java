package com.eunoia.insight.application;

import com.eunoia.insight.application.dto.MetaAnalysisGenerationRequested;
import com.eunoia.insight.domain.MetaAnalysisGenerationStatus;
import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaAnalysisGenerationClaimerTest {

    private static final LocalDate PERIOD_END = LocalDate.of(2026, 10, 6);
    private static final List<Long> ENTRY_IDS = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

    @Mock
    private MetaAnalysisResultRepository metaAnalysisResultRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MetaAnalysisGenerationClaimer claimer;

    @BeforeEach
    void setUp() {
        claimer = new MetaAnalysisGenerationClaimer(metaAnalysisResultRepository, eventPublisher);
    }

    @Test
    @DisplayName("선점하면 PROCESSING 행을 저장하고, 저장된 행의 id로 이벤트를 발행한다(이벤트에는 id만).")
    void claim_savesProcessingRow_andPublishesEventWithRowId() {
        when(metaAnalysisResultRepository.save(any())).thenAnswer(invocation -> {
            MetaAnalysisResult saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        MetaAnalysisResult result = claimer.claim(1L, PERIOD_END.minusDays(29), PERIOD_END, 3, 10, 2, ENTRY_IDS);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getGenerationStatus()).isEqualTo(MetaAnalysisGenerationStatus.PROCESSING);
        assertThat(result.getAttemptNo()).isEqualTo(3);
        assertThat(result.getBasedOnCount()).isEqualTo(10);
        assertThat(result.getExcludedEntryCount()).isEqualTo(2);
        assertThat(result.getSelectedEntryIds()).containsExactlyElementsOf(ENTRY_IDS);

        ArgumentCaptor<MetaAnalysisGenerationRequested> event = ArgumentCaptor.forClass(MetaAnalysisGenerationRequested.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().resultId()).isEqualTo(42L);
    }
}
