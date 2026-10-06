package com.eunoia.insight.application;

import com.eunoia.analysis.query.EmotionAnalysisCandidate;
import com.eunoia.analysis.query.EmotionAnalysisQueryApi;
import com.eunoia.insight.application.dto.MetaAnalysisGenerationRequested;
import com.eunoia.insight.domain.MetaAnalysisAiResponse;
import com.eunoia.insight.domain.MetaAnalysisAnalyzer;
import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisResultRepository;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaAnalysisGenerationWorkerTest {

    private static final long RESULT_ID = 7L;
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 10, 6);
    private static final List<Long> ENTRY_IDS = List.of(10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L, 1L); // 일부러 내림차순(저장 순서 유지 확인)

    @Mock
    private MetaAnalysisResultRepository metaAnalysisResultRepository;

    @Mock
    private EmotionAnalysisQueryApi analysisQueryApi;

    @Mock
    private EmotionEntryQueryApi journalQueryApi;

    @Mock
    private MetaAnalysisAnalyzer analyzer;

    @Mock
    private MetaAnalysisResultRecorder resultRecorder;

    private MetaAnalysisGenerationWorker worker;

    @BeforeEach
    void setUp() {
        worker = new MetaAnalysisGenerationWorker(
                metaAnalysisResultRepository, analysisQueryApi, journalQueryApi, analyzer, resultRecorder);
    }

    private MetaAnalysisGenerationRequested event() {
        return new MetaAnalysisGenerationRequested(RESULT_ID);
    }

    private MetaAnalysisResult processing() {
        return MetaAnalysisResult.start(1L, PERIOD_END.minusDays(29), PERIOD_END, 1, 10, 2, ENTRY_IDS);
    }

    private List<EmotionAnalysisCandidate> candidates() {
        return IntStream.rangeClosed(1, 10)
                .mapToObj(i -> new EmotionAnalysisCandidate((long) i, PERIOD_END.minusDays(10 - i), 90, "이유" + i))
                .toList();
    }

    private List<EmotionEntryContent> contents() {
        return IntStream.rangeClosed(1, 10).mapToObj(i -> new EmotionEntryContent((long) i, "내용" + i)).toList();
    }

    private MetaAnalysisAiResponse aiResponse() {
        return new MetaAnalysisAiResponse(
                new MetaAnalysisContent.Outer("겉모습 요약", List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new MetaAnalysisContent.Inner("내면 요약", List.of(), List.of(), List.of(), List.of(), "", ""),
                new MetaAnalysisAiResponse.ClarityNarrative(List.of("이유"), List.of(), List.of()));
    }

    private void givenInputsAvailable() {
        when(metaAnalysisResultRepository.findById(RESULT_ID)).thenReturn(Optional.of(processing()));
        when(analysisQueryApi.findSuccessfulAnalyses(eq(1L), any(), any())).thenReturn(candidates());
        when(journalQueryApi.findContentsByEntryIds(eq(1L), any())).thenReturn(contents());
    }

    @Test
    @DisplayName("처리 중인 행이면 저장된 일기 목록으로 GPT를 호출하고 결과를 성공으로 기록한다(저장된 순서 유지).")
    void handle_withProcessingRow_buildsContentAndRecordsSuccess() {
        givenInputsAvailable();
        when(analyzer.analyze(any())).thenReturn(aiResponse());

        worker.handle(event());

        ArgumentCaptor<MetaAnalysisContent> captor = ArgumentCaptor.forClass(MetaAnalysisContent.class);
        verify(resultRecorder).recordSuccess(eq(RESULT_ID), captor.capture());
        verify(resultRecorder, never()).recordFailure(any(), any());
        MetaAnalysisContent content = captor.getValue();
        assertThat(content.outer().summary()).isEqualTo("겉모습 요약");
        assertThat(content.evidence()).extracting(MetaAnalysisContent.RepresentativeEntry::entryId)
                .containsExactly(10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L, 1L);
        assertThat(content.clarity().clarityScore()).isEqualTo(90);
    }

    @Test
    @DisplayName("행을 찾을 수 없으면 아무것도 하지 않는다.")
    void handle_withMissingRow_doesNothing() {
        when(metaAnalysisResultRepository.findById(RESULT_ID)).thenReturn(Optional.empty());

        worker.handle(event());

        verifyNoInteractions(analysisQueryApi, journalQueryApi, analyzer, resultRecorder);
    }

    @Test
    @DisplayName("이미 종결된(성공·실패) 행은 다시 처리하지 않는다(이벤트 재전달 멱등).")
    void handle_withTerminalRow_doesNothing() {
        MetaAnalysisResult failed = processing();
        failed.markFailed("이전 실패");
        when(metaAnalysisResultRepository.findById(RESULT_ID)).thenReturn(Optional.of(failed));

        worker.handle(event());

        verifyNoInteractions(analysisQueryApi, journalQueryApi, analyzer, resultRecorder);
    }

    @Test
    @DisplayName("선택된 일기의 분석이 사라졌으면(삭제됨) GPT를 호출하지 않고 실패로 기록한다.")
    void handle_whenSelectedAnalysisMissing_recordsFailureWithoutCallingAnalyzer() {
        when(metaAnalysisResultRepository.findById(RESULT_ID)).thenReturn(Optional.of(processing()));
        when(analysisQueryApi.findSuccessfulAnalyses(eq(1L), any(), any()))
                .thenReturn(candidates().subList(0, 9)); // 10번 일기의 분석이 없음

        worker.handle(event());

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(resultRecorder).recordFailure(eq(RESULT_ID), reason.capture());
        assertThat(reason.getValue()).contains("entryId=10");
        verifyNoInteractions(analyzer);
        verify(resultRecorder, never()).recordSuccess(any(), any());
    }

    @Test
    @DisplayName("일기 원문이 일부 없으면(모듈 간 정합성 깨짐) GPT를 호출하지 않고 실패로 기록한다.")
    void handle_whenJournalContentMissing_recordsFailureWithoutCallingAnalyzer() {
        when(metaAnalysisResultRepository.findById(RESULT_ID)).thenReturn(Optional.of(processing()));
        when(analysisQueryApi.findSuccessfulAnalyses(eq(1L), any(), any())).thenReturn(candidates());
        when(journalQueryApi.findContentsByEntryIds(eq(1L), any()))
                .thenReturn(List.of(new EmotionEntryContent(1L, "내용1")));

        worker.handle(event());

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(resultRecorder).recordFailure(eq(RESULT_ID), reason.capture());
        assertThat(reason.getValue()).contains("entryId=");
        verifyNoInteractions(analyzer);
    }

    @Test
    @DisplayName("GPT 호출이 예외를 던지면 그 메시지로 실패를 기록하고 성공은 기록하지 않는다.")
    void handle_whenAnalyzerThrows_recordsFailure() {
        givenInputsAvailable();
        when(analyzer.analyze(any())).thenThrow(new RuntimeException("GPT 호출 실패"));

        worker.handle(event());

        verify(resultRecorder).recordFailure(RESULT_ID, "GPT 호출 실패");
        verify(resultRecorder, never()).recordSuccess(any(), any());
    }

    @Test
    @DisplayName("실패 원인 메시지가 없으면 '원인 불명'으로 기록한다.")
    void handle_whenFailureHasNoMessage_recordsUnknownReason() {
        givenInputsAvailable();
        when(analyzer.analyze(any())).thenThrow(new RuntimeException());

        worker.handle(event());

        verify(resultRecorder).recordFailure(RESULT_ID, "원인 불명");
    }

    @Test
    @DisplayName("실패 원인 메시지가 컬럼 길이(1000자)를 넘으면 잘라서 기록한다.")
    void handle_whenFailureMessageTooLong_truncatesReason() {
        givenInputsAvailable();
        when(analyzer.analyze(any())).thenThrow(new RuntimeException("x".repeat(1500)));

        worker.handle(event());

        verify(resultRecorder).recordFailure(RESULT_ID, "x".repeat(1000));
    }

    @Test
    @DisplayName("GPT 응답의 목록 필드가 null이어도(근거 없음) 실패로 처리하지 않고 빈 목록으로 성공 기록한다.")
    void handle_whenAiResponseHasNullLists_recordsSuccessWithEmptyLists() {
        givenInputsAvailable();
        when(analyzer.analyze(any())).thenReturn(new MetaAnalysisAiResponse(
                new MetaAnalysisContent.Outer("겉모습 요약", null, null, null, null, null, null),
                new MetaAnalysisContent.Inner("내면 요약", null, null, null, null, null, null),
                new MetaAnalysisAiResponse.ClarityNarrative(null, null, null)));

        worker.handle(event());

        ArgumentCaptor<MetaAnalysisContent> captor = ArgumentCaptor.forClass(MetaAnalysisContent.class);
        verify(resultRecorder).recordSuccess(eq(RESULT_ID), captor.capture());
        verify(resultRecorder, never()).recordFailure(any(), any());
        assertThat(captor.getValue().outer().keywords()).isEmpty();
        assertThat(captor.getValue().inner().needs()).isEmpty();
        assertThat(captor.getValue().clarity().clarityReasons()).isEmpty();
        assertThat(captor.getValue().evidence()).hasSize(10);
    }
}
