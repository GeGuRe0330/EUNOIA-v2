package com.eunoia.insight.application;

import com.eunoia.analysis.query.EmotionAnalysisCandidate;
import com.eunoia.analysis.query.EmotionAnalysisQueryApi;
import com.eunoia.insight.application.dto.MetaAnalysisHistoryItem;
import com.eunoia.insight.application.dto.MetaAnalysisInfo;
import com.eunoia.insight.domain.MetaAnalysisAiResponse;
import com.eunoia.insight.domain.MetaAnalysisAnalyzer;
import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisResultRepository;
import com.eunoia.insight.domain.MetaAnalysisStatus;
import com.eunoia.journal.query.EmotionEntryContent;
import com.eunoia.journal.query.EmotionEntryQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaAnalysisServiceTest {

    private static final LocalDate ENTRY_DATE = LocalDate.of(2026, 9, 20);

    @Mock
    private EmotionAnalysisQueryApi analysisQueryApi;

    @Mock
    private EmotionEntryQueryApi journalQueryApi;

    @Mock
    private MetaAnalysisCandidateSelector candidateSelector;

    @Mock
    private MetaAnalysisRegenerationGuard regenerationGuard;

    @Mock
    private MetaAnalysisAnalyzer analyzer;

    @Mock
    private MetaAnalysisResultRepository metaAnalysisResultRepository;

    private MetaAnalysisService metaAnalysisService;

    @BeforeEach
    void setUp() {
        metaAnalysisService = new MetaAnalysisService(
                analysisQueryApi, journalQueryApi, candidateSelector, regenerationGuard, analyzer, metaAnalysisResultRepository);
    }

    @Test
    @DisplayName("선택된 후보가 10건 미만이면 PREPARING을 반환하고 GPT를 호출하지 않는다.")
    void generate_withFewerThanTenSelected_returnsPreparingWithoutCallingAnalyzer() {
        when(analysisQueryApi.findSuccessfulAnalyses(any(), any(), any())).thenReturn(List.of());
        when(candidateSelector.select(any()))
                .thenReturn(new MetaAnalysisSelection(List.of(candidate(1L, ENTRY_DATE, 90)), 0));

        MetaAnalysisInfo result = metaAnalysisService.generate(1L);

        assertThat(result.status()).isEqualTo(MetaAnalysisStatus.PREPARING);
        assertThat(result.currentCount()).isEqualTo(1);
        verifyNoInteractions(analyzer, journalQueryApi);
        verify(metaAnalysisResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("10건이 선택되면 journal 원문을 조회하고 GPT를 호출해 저장한 뒤 READY를 반환한다.")
    void generate_withTenSelected_callsJournalAndAnalyzerThenSaves() {
        List<EmotionAnalysisCandidate> selected = tenCandidates();
        when(analysisQueryApi.findSuccessfulAnalyses(any(), any(), any())).thenReturn(selected);
        when(candidateSelector.select(any())).thenReturn(new MetaAnalysisSelection(selected, 2));
        when(metaAnalysisResultRepository.findLatestByMemberId(1L)).thenReturn(Optional.empty());
        when(regenerationGuard.isUnchanged(any(), anyInt(), any())).thenReturn(false);
        when(journalQueryApi.findContentsByEntryIds(eq(1L), any())).thenReturn(journalContentsFor(selected));
        when(analyzer.analyze(any())).thenReturn(stubAiResponse());
        when(metaAnalysisResultRepository.findByMemberIdAndPeriodEnd(any(), any())).thenReturn(Optional.empty());
        when(metaAnalysisResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MetaAnalysisInfo result = metaAnalysisService.generate(1L);

        assertThat(result.status()).isEqualTo(MetaAnalysisStatus.READY);
        verify(journalQueryApi).findContentsByEntryIds(eq(1L), any());
        verify(analyzer).analyze(any());
        verify(metaAnalysisResultRepository).save(any());
    }

    @Test
    @DisplayName("직전 결과와 entryId·제외 건수가 동일하면 재생성하지 않고 기존 결과를 그대로 반환한다.")
    void generate_withUnchangedSelection_returnsExistingResultWithoutRegenerating() {
        List<EmotionAnalysisCandidate> selected = tenCandidates();
        MetaAnalysisResult existing = existingResult(selected, 2);
        when(analysisQueryApi.findSuccessfulAnalyses(any(), any(), any())).thenReturn(selected);
        when(candidateSelector.select(any())).thenReturn(new MetaAnalysisSelection(selected, 2));
        when(metaAnalysisResultRepository.findLatestByMemberId(1L)).thenReturn(Optional.of(existing));
        when(regenerationGuard.isUnchanged(any(), anyInt(), any())).thenReturn(true);

        MetaAnalysisInfo result = metaAnalysisService.generate(1L);

        assertThat(result.status()).isEqualTo(MetaAnalysisStatus.READY);
        verifyNoInteractions(analyzer, journalQueryApi);
        verify(metaAnalysisResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 기간(periodEnd)에 이미 결과가 있으면 새로 만들지 않고 기존 행을 갱신한다.")
    void generate_withExistingRowForSamePeriodEnd_updatesInPlace() {
        List<EmotionAnalysisCandidate> selected = tenCandidates();
        MetaAnalysisResult existing = existingResult(selected, 2);
        when(analysisQueryApi.findSuccessfulAnalyses(any(), any(), any())).thenReturn(selected);
        when(candidateSelector.select(any())).thenReturn(new MetaAnalysisSelection(selected, 2));
        when(metaAnalysisResultRepository.findLatestByMemberId(1L)).thenReturn(Optional.empty());
        when(regenerationGuard.isUnchanged(any(), anyInt(), any())).thenReturn(false);
        when(journalQueryApi.findContentsByEntryIds(eq(1L), any())).thenReturn(journalContentsFor(selected));
        when(analyzer.analyze(any())).thenReturn(stubAiResponse());
        when(metaAnalysisResultRepository.findByMemberIdAndPeriodEnd(any(), any())).thenReturn(Optional.of(existing));
        ArgumentCaptor<MetaAnalysisResult> captor = ArgumentCaptor.forClass(MetaAnalysisResult.class);
        when(metaAnalysisResultRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        metaAnalysisService.generate(1L);

        assertThat(captor.getValue()).isSameAs(existing);
    }

    @Test
    @DisplayName("같은 기간에 기존 결과가 없으면 새 행을 만들어 저장한다.")
    void generate_withNoExistingRowForPeriodEnd_createsNewRow() {
        List<EmotionAnalysisCandidate> selected = tenCandidates();
        when(analysisQueryApi.findSuccessfulAnalyses(any(), any(), any())).thenReturn(selected);
        when(candidateSelector.select(any())).thenReturn(new MetaAnalysisSelection(selected, 2));
        when(metaAnalysisResultRepository.findLatestByMemberId(1L)).thenReturn(Optional.empty());
        when(regenerationGuard.isUnchanged(any(), anyInt(), any())).thenReturn(false);
        when(journalQueryApi.findContentsByEntryIds(eq(1L), any())).thenReturn(journalContentsFor(selected));
        when(analyzer.analyze(any())).thenReturn(stubAiResponse());
        when(metaAnalysisResultRepository.findByMemberIdAndPeriodEnd(any(), any())).thenReturn(Optional.empty());
        ArgumentCaptor<MetaAnalysisResult> captor = ArgumentCaptor.forClass(MetaAnalysisResult.class);
        when(metaAnalysisResultRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        metaAnalysisService.generate(1L);

        assertThat(captor.getValue().getId()).isNull();
    }

    @Test
    @DisplayName("journal이 일부 entry만 반환하면(모듈 간 정합성 깨짐) GPT를 호출하지 않고 즉시 실패한다.")
    void generate_withPartialJournalResponse_failsFastWithoutCallingAnalyzer() {
        List<EmotionAnalysisCandidate> selected = tenCandidates();
        when(analysisQueryApi.findSuccessfulAnalyses(any(), any(), any())).thenReturn(selected);
        when(candidateSelector.select(any())).thenReturn(new MetaAnalysisSelection(selected, 2));
        when(metaAnalysisResultRepository.findLatestByMemberId(1L)).thenReturn(Optional.empty());
        when(regenerationGuard.isUnchanged(any(), anyInt(), any())).thenReturn(false);
        when(journalQueryApi.findContentsByEntryIds(eq(1L), any()))
                .thenReturn(List.of(new EmotionEntryContent(selected.get(0).entryId(), "내용")));

        assertThatThrownBy(() -> metaAnalysisService.generate(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("entryId=");

        verifyNoInteractions(analyzer);
        verify(metaAnalysisResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("과거 메타분석 결과를 최신순으로 조회한다.")
    void getHistory_returnsResultsOrderedByRepository() {
        MetaAnalysisResult older = existingResult(tenCandidates(), 1);
        MetaAnalysisResult newer = existingResult(tenCandidates(), 3);
        when(metaAnalysisResultRepository.findAllByMemberIdOrderByPeriodEndDesc(1L))
                .thenReturn(List.of(newer, older));

        List<MetaAnalysisHistoryItem> result = metaAnalysisService.getHistory(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).excludedEntryCount()).isEqualTo(3);
        assertThat(result.get(1).excludedEntryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("GPT 호출이 예외를 던지면 그대로 전파되고 아무것도 저장하지 않는다.")
    void generate_whenAnalyzerThrows_propagatesExceptionWithoutSaving() {
        List<EmotionAnalysisCandidate> selected = tenCandidates();
        when(analysisQueryApi.findSuccessfulAnalyses(any(), any(), any())).thenReturn(selected);
        when(candidateSelector.select(any())).thenReturn(new MetaAnalysisSelection(selected, 2));
        when(metaAnalysisResultRepository.findLatestByMemberId(1L)).thenReturn(Optional.empty());
        when(regenerationGuard.isUnchanged(any(), anyInt(), any())).thenReturn(false);
        when(journalQueryApi.findContentsByEntryIds(eq(1L), any())).thenReturn(journalContentsFor(selected));
        when(analyzer.analyze(any())).thenThrow(new RuntimeException("GPT 호출 실패"));

        assertThatThrownBy(() -> metaAnalysisService.generate(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("GPT 호출 실패");

        verify(metaAnalysisResultRepository, never()).save(any());
    }

    private EmotionAnalysisCandidate candidate(Long entryId, LocalDate entryDate, int score) {
        return new EmotionAnalysisCandidate(entryId, entryDate, score, "이유");
    }

    private List<EmotionAnalysisCandidate> tenCandidates() {
        LocalDate start = ENTRY_DATE.minusDays(9);
        return java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> candidate((long) (i + 1), start.plusDays(i), 90))
                .toList();
    }

    private List<EmotionEntryContent> journalContentsFor(List<EmotionAnalysisCandidate> selected) {
        return selected.stream()
                .map(c -> new EmotionEntryContent(c.entryId(), "내용" + c.entryId()))
                .toList();
    }

    private MetaAnalysisAiResponse stubAiResponse() {
        return new MetaAnalysisAiResponse(
                new MetaAnalysisContent.Outer("겉모습 요약", List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new MetaAnalysisContent.Inner("내면 요약", List.of(), List.of(), List.of(), List.of(), "", ""),
                new MetaAnalysisAiResponse.ClarityNarrative(List.of("이유"), List.of(), List.of()));
    }

    private MetaAnalysisResult existingResult(List<EmotionAnalysisCandidate> selected, int excludedEntryCount) {
        List<MetaAnalysisContent.RepresentativeEntry> evidence = selected.stream()
                .map(c -> new MetaAnalysisContent.RepresentativeEntry(c.entryId(), "이유"))
                .toList();
        MetaAnalysisContent content = new MetaAnalysisContent(
                new MetaAnalysisContent.Outer("요약", List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new MetaAnalysisContent.Inner("요약", List.of(), List.of(), List.of(), List.of(), "", ""),
                new MetaAnalysisContent.Clarity(80, List.of(), List.of(), List.of()),
                evidence);
        return MetaAnalysisResult.create(1L, ENTRY_DATE.minusDays(29), ENTRY_DATE, selected.size(), excludedEntryCount, content);
    }
}
