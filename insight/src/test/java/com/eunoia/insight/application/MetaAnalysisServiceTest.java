package com.eunoia.insight.application;

import com.eunoia.analysis.query.EmotionAnalysisCandidate;
import com.eunoia.analysis.query.EmotionAnalysisQueryApi;
import com.eunoia.insight.application.dto.MetaAnalysisHistoryItem;
import com.eunoia.insight.application.dto.MetaAnalysisInfo;
import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisGenerationStatus;
import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisResultRepository;
import com.eunoia.insight.domain.MetaAnalysisStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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
    private MetaAnalysisCandidateSelector candidateSelector;

    @Mock
    private MetaAnalysisRegenerationGuard regenerationGuard;

    @Mock
    private MetaAnalysisGenerationClaimer generationClaimer;

    @Mock
    private MetaAnalysisResultRepository metaAnalysisResultRepository;

    private MetaAnalysisService metaAnalysisService;

    @BeforeEach
    void setUp() {
        metaAnalysisService = new MetaAnalysisService(
                analysisQueryApi, candidateSelector, regenerationGuard, generationClaimer, metaAnalysisResultRepository);
    }

    private void givenTenSelected(int excludedEntryCount) {
        List<EmotionAnalysisCandidate> selected = tenCandidates();
        when(analysisQueryApi.findSuccessfulAnalyses(any(), any(), any())).thenReturn(selected);
        when(candidateSelector.select(any())).thenReturn(new MetaAnalysisSelection(selected, excludedEntryCount));
    }

    @Test
    @DisplayName("선택된 후보가 10건 미만이면 PREPARING을 반환하고 작업을 만들지 않는다.")
    void generate_withFewerThanTenSelected_returnsPreparingWithoutClaiming() {
        when(analysisQueryApi.findSuccessfulAnalyses(any(), any(), any())).thenReturn(List.of());
        when(candidateSelector.select(any()))
                .thenReturn(new MetaAnalysisSelection(List.of(candidate(1L, ENTRY_DATE, 90)), 0));

        MetaAnalysisInfo result = metaAnalysisService.generate(1L);

        assertThat(result.status()).isEqualTo(MetaAnalysisStatus.PREPARING);
        assertThat(result.currentCount()).isEqualTo(1);
        assertThat(result.generationStatus()).isNull();
        verifyNoInteractions(generationClaimer);
    }

    @Test
    @DisplayName("직전 결과와 entryId·제외 건수가 동일하면 새로 만들지 않고 기존 결과를 그대로 반환한다.")
    void generate_withUnchangedSelection_returnsExistingResultWithoutClaiming() {
        givenTenSelected(2);
        MetaAnalysisResult existing = successResult(1, 2);
        when(metaAnalysisResultRepository.findLatestSuccessByMemberId(1L)).thenReturn(Optional.of(existing));
        when(metaAnalysisResultRepository.findLatestAttemptOfDay(eq(1L), any())).thenReturn(Optional.of(existing));
        when(regenerationGuard.isUnchanged(any(), anyInt(), any())).thenReturn(true);

        MetaAnalysisInfo result = metaAnalysisService.generate(1L);

        assertThat(result.status()).isEqualTo(MetaAnalysisStatus.READY);
        assertThat(result.content()).isNotNull();
        assertThat(result.generationStatus()).isNull();
        verifyNoInteractions(generationClaimer);
    }

    @Test
    @DisplayName("오늘 첫 생성이면 attemptNo 1로 새 작업을 선점하고 PROCESSING 상태를 반환한다.")
    void generate_withNoAttemptToday_claimsFirstAttempt() {
        givenTenSelected(2);
        when(metaAnalysisResultRepository.findLatestSuccessByMemberId(1L)).thenReturn(Optional.empty());
        when(metaAnalysisResultRepository.findLatestAttemptOfDay(eq(1L), any())).thenReturn(Optional.empty());
        when(regenerationGuard.isUnchanged(any(), anyInt(), any())).thenReturn(false);
        MetaAnalysisResult started = processingResult(1);
        when(generationClaimer.claim(eq(1L), any(), any(), eq(1), eq(10), eq(2), any())).thenReturn(started);

        MetaAnalysisInfo result = metaAnalysisService.generate(1L);

        assertThat(result.status()).isEqualTo(MetaAnalysisStatus.READY);
        assertThat(result.generationStatus()).isEqualTo(MetaAnalysisGenerationStatus.PROCESSING);
        assertThat(result.content()).isNull();
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(generationClaimer).claim(eq(1L), any(), any(), eq(1), eq(10), eq(2), idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    @Test
    @DisplayName("오늘 직전 시도가 실패했으면 attemptNo를 1 올려 새 작업을 선점한다(재요청 허용).")
    void generate_afterFailedAttempt_claimsNextAttempt() {
        givenTenSelected(0);
        MetaAnalysisResult failed = processingResult(1);
        failed.markFailed("GPT 호출 실패");
        when(metaAnalysisResultRepository.findLatestSuccessByMemberId(1L)).thenReturn(Optional.empty());
        when(metaAnalysisResultRepository.findLatestAttemptOfDay(eq(1L), any())).thenReturn(Optional.of(failed));
        when(regenerationGuard.isUnchanged(any(), anyInt(), any())).thenReturn(false);
        when(generationClaimer.claim(eq(1L), any(), any(), eq(2), anyInt(), anyInt(), any())).thenReturn(processingResult(2));

        MetaAnalysisInfo result = metaAnalysisService.generate(1L);

        assertThat(result.generationStatus()).isEqualTo(MetaAnalysisGenerationStatus.PROCESSING);
        verify(generationClaimer).claim(eq(1L), any(), any(), eq(2), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("오늘 이미 진행 중인 작업이 있으면 새로 선점하지 않고 그 상태와 이전 결과를 반환한다.")
    void generate_withProcessingAttempt_returnsCurrentStateWithoutClaiming() {
        givenTenSelected(2);
        MetaAnalysisResult previous = successResult(1, 2);
        when(metaAnalysisResultRepository.findLatestSuccessByMemberId(1L)).thenReturn(Optional.of(previous));
        when(metaAnalysisResultRepository.findLatestAttemptOfDay(eq(1L), any())).thenReturn(Optional.of(processingResult(2)));
        when(regenerationGuard.isUnchanged(any(), anyInt(), any())).thenReturn(false);

        MetaAnalysisInfo result = metaAnalysisService.generate(1L);

        assertThat(result.generationStatus()).isEqualTo(MetaAnalysisGenerationStatus.PROCESSING);
        assertThat(result.content()).isNotNull(); // 진행 중에도 이전 결과가 보인다
        verifyNoInteractions(generationClaimer);
    }

    @Test
    @DisplayName("동시 요청이 먼저 같은 시도 번호를 만들어 유니크 위반이 나면, 그 진행 중인 작업의 상태를 반환한다.")
    void generate_whenConcurrentClaimWins_returnsConcurrentProcessingState() {
        givenTenSelected(0);
        when(metaAnalysisResultRepository.findLatestSuccessByMemberId(1L)).thenReturn(Optional.empty());
        when(metaAnalysisResultRepository.findLatestAttemptOfDay(eq(1L), any()))
                .thenReturn(Optional.empty(), Optional.of(processingResult(1)));
        when(regenerationGuard.isUnchanged(any(), anyInt(), any())).thenReturn(false);
        when(generationClaimer.claim(any(), any(), any(), anyInt(), anyInt(), anyInt(), any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        MetaAnalysisInfo result = metaAnalysisService.generate(1L);

        assertThat(result.generationStatus()).isEqualTo(MetaAnalysisGenerationStatus.PROCESSING);
    }

    @Test
    @DisplayName("무결성 위반인데 진행 중인 작업을 찾을 수 없으면 원래 예외를 그대로 던진다.")
    void generate_whenIntegrityViolationWithoutProcessingAttempt_rethrows() {
        givenTenSelected(0);
        when(metaAnalysisResultRepository.findLatestSuccessByMemberId(1L)).thenReturn(Optional.empty());
        when(metaAnalysisResultRepository.findLatestAttemptOfDay(eq(1L), any())).thenReturn(Optional.empty());
        when(regenerationGuard.isUnchanged(any(), anyInt(), any())).thenReturn(false);
        when(generationClaimer.claim(any(), any(), any(), anyInt(), anyInt(), anyInt(), any()))
                .thenThrow(new DataIntegrityViolationException("다른 무결성 위반"));

        assertThatThrownBy(() -> metaAnalysisService.generate(1L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("다른 무결성 위반");
    }

    @Test
    @DisplayName("최신 조회는 진행 중인 작업이 있으면 PROCESSING과 이전 결과를 함께 돌려준다.")
    void getLatest_withProcessingAttempt_includesGenerationStatusAndPreviousContent() {
        givenTenSelected(0);
        when(metaAnalysisResultRepository.findLatestSuccessByMemberId(1L)).thenReturn(Optional.of(successResult(1, 0)));
        when(metaAnalysisResultRepository.findLatestAttemptOfDay(eq(1L), any())).thenReturn(Optional.of(processingResult(2)));

        MetaAnalysisInfo result = metaAnalysisService.getLatest(1L);

        assertThat(result.status()).isEqualTo(MetaAnalysisStatus.READY);
        assertThat(result.generationStatus()).isEqualTo(MetaAnalysisGenerationStatus.PROCESSING);
        assertThat(result.generationReason()).isNull();
        assertThat(result.content()).isNotNull();
    }

    @Test
    @DisplayName("최신 조회는 오늘 마지막 시도가 실패면 FAILED와 고정 문구를 돌려준다.")
    void getLatest_withFailedAttempt_includesFailedStatusAndFixedReason() {
        givenTenSelected(0);
        MetaAnalysisResult failed = processingResult(1);
        failed.markFailed("내부 사유는 노출하지 않는다");
        when(metaAnalysisResultRepository.findLatestSuccessByMemberId(1L)).thenReturn(Optional.empty());
        when(metaAnalysisResultRepository.findLatestAttemptOfDay(eq(1L), any())).thenReturn(Optional.of(failed));

        MetaAnalysisInfo result = metaAnalysisService.getLatest(1L);

        assertThat(result.generationStatus()).isEqualTo(MetaAnalysisGenerationStatus.FAILED);
        assertThat(result.generationReason()).isEqualTo("메타분석 생성에 실패했어요.");
        assertThat(result.content()).isNull();
    }

    @Test
    @DisplayName("최신 조회는 오늘 마지막 시도가 성공이면 작업 상태가 없다(null).")
    void getLatest_withSuccessfulAttempt_hasNoGenerationStatus() {
        givenTenSelected(0);
        MetaAnalysisResult success = successResult(1, 0);
        when(metaAnalysisResultRepository.findLatestSuccessByMemberId(1L)).thenReturn(Optional.of(success));
        when(metaAnalysisResultRepository.findLatestAttemptOfDay(eq(1L), any())).thenReturn(Optional.of(success));

        MetaAnalysisInfo result = metaAnalysisService.getLatest(1L);

        assertThat(result.generationStatus()).isNull();
        assertThat(result.generationReason()).isNull();
    }

    @Test
    @DisplayName("과거 메타분석 결과를 저장소가 준 순서(최신순) 그대로 조회한다.")
    void getHistory_returnsResultsOrderedByRepository() {
        MetaAnalysisResult older = successResult(1, 1);
        MetaAnalysisResult newer = successResult(1, 3);
        when(metaAnalysisResultRepository.findAllSuccessByMemberId(1L)).thenReturn(List.of(newer, older));

        List<MetaAnalysisHistoryItem> result = metaAnalysisService.getHistory(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).excludedEntryCount()).isEqualTo(3);
        assertThat(result.get(1).excludedEntryCount()).isEqualTo(1);
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

    private MetaAnalysisContent content() {
        List<MetaAnalysisContent.RepresentativeEntry> evidence = tenCandidates().stream()
                .map(c -> new MetaAnalysisContent.RepresentativeEntry(c.entryId(), c.entryDate(), "이유"))
                .toList();
        return new MetaAnalysisContent(
                new MetaAnalysisContent.Outer("요약", List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new MetaAnalysisContent.Inner("요약", List.of(), List.of(), List.of(), List.of(), "", ""),
                new MetaAnalysisContent.Clarity(80, List.of(), List.of(), List.of()),
                evidence);
    }

    private MetaAnalysisResult successResult(int attemptNo, int excludedEntryCount) {
        MetaAnalysisResult result = MetaAnalysisResult.start(1L, ENTRY_DATE.minusDays(29), ENTRY_DATE, attemptNo,
                10, excludedEntryCount, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
        result.complete(content());
        return result;
    }

    private MetaAnalysisResult processingResult(int attemptNo) {
        return MetaAnalysisResult.start(1L, ENTRY_DATE.minusDays(29), ENTRY_DATE, attemptNo,
                10, 0, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));
    }
}
