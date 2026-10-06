package com.eunoia.insight.application;

import com.eunoia.analysis.query.EmotionAnalysisCandidate;
import com.eunoia.analysis.query.EmotionAnalysisQueryApi;
import com.eunoia.insight.application.dto.MetaAnalysisGenerationRequested;
import com.eunoia.insight.domain.MetaAnalysisAiResponse;
import com.eunoia.insight.domain.MetaAnalysisAnalyzer;
import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisGenerationStatus;
import com.eunoia.insight.domain.MetaAnalysisInput;
import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisResultRepository;
import com.eunoia.journal.query.EmotionEntryContent;
import com.eunoia.journal.query.EmotionEntryQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetaAnalysisGenerationWorker {

    private static final String UNKNOWN_FAILURE_REASON = "원인 불명";
    // MetaAnalysisResult.failureReason 컬럼 길이(length = 1000)와 반드시 맞춰야 함
    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final MetaAnalysisResultRepository metaAnalysisResultRepository;
    private final EmotionAnalysisQueryApi analysisQueryApi;
    private final EmotionEntryQueryApi journalQueryApi;
    private final MetaAnalysisAnalyzer analyzer;
    private final MetaAnalysisResultRecorder resultRecorder;

    // 바깥 트랜잭션 없이 실행 — GPT 호출이 트랜잭션·DB 커넥션 안에 들어가지 않게(@ApplicationModuleListener로 안 묶는 이유)
    @Async
    @TransactionalEventListener
    public void handle(MetaAnalysisGenerationRequested event) {
        Optional<MetaAnalysisResult> processing = metaAnalysisResultRepository.findById(event.resultId())
                .filter(result -> result.getGenerationStatus() == MetaAnalysisGenerationStatus.PROCESSING);
        if (processing.isEmpty()) {
            return; // 이미 종결 상태 — 다시 처리하지 않는다(멱등)
        }

        MetaAnalysisContent content;
        try {
            content = buildContent(processing.get());
        } catch (RuntimeException e) {
            // 사유는 DB(failure_reason)에도 남지만 콘솔에서 바로 원인을 볼 수 있게 로그로도 남긴다
            log.warn("메타분석 생성 실패 resultId={}: {}", event.resultId(), e.getMessage(), e);
            resultRecorder.recordFailure(event.resultId(), failureReason(e));
            return;
        }
        resultRecorder.recordSuccess(event.resultId(), content);
    }

    private MetaAnalysisContent buildContent(MetaAnalysisResult processing) {
        Long memberId = processing.getMemberId();
        List<Long> entryIds = processing.getSelectedEntryIds();
        int excludedEntryCount = processing.getExcludedEntryCount();

        // POST 시점에 고른 일기들을 그대로 쓴다(저장해 둔 순서 유지). 그 사이 삭제돼 하나라도 없으면 실패 — 다시 누르면 새 구성으로 만든다
        Map<Long, EmotionAnalysisCandidate> candidatesById = analysisQueryApi
                .findSuccessfulAnalyses(memberId, processing.getPeriodStart(), processing.getPeriodEnd()).stream()
                .collect(Collectors.toMap(EmotionAnalysisCandidate::entryId, Function.identity()));
        List<EmotionAnalysisCandidate> selected = entryIds.stream()
                .map(entryId -> Optional.ofNullable(candidatesById.get(entryId))
                        .orElseThrow(() -> new IllegalStateException(
                                "선택된 일기의 분석을 찾을 수 없습니다. entryId=" + entryId)))
                .toList();

        Map<Long, String> contentsByEntryId = journalQueryApi.findContentsByEntryIds(memberId, entryIds).stream()
                .collect(Collectors.toMap(EmotionEntryContent::entryId, EmotionEntryContent::content));

        List<String> entryContents = selected.stream()
                .map(candidate -> Optional.ofNullable(contentsByEntryId.get(candidate.entryId()))
                        .orElseThrow(() -> new IllegalStateException(
                                "선택된 일기의 원문을 찾을 수 없습니다. entryId=" + candidate.entryId())))
                .toList();

        int clarityScoreAverage = calculateClarityScoreAverage(selected);

        MetaAnalysisInput input = new MetaAnalysisInput(entryContents, excludedEntryCount, clarityScoreAverage);
        MetaAnalysisAiResponse aiResponse = analyzer.analyze(input);

        List<MetaAnalysisContent.RepresentativeEntry> evidence = selected.stream()
                .map(candidate -> new MetaAnalysisContent.RepresentativeEntry(
                        candidate.entryId(), candidate.entryDate(), resolveWhySelected(candidate.entryClarityReason())))
                .toList();

        MetaAnalysisContent.Clarity clarity = new MetaAnalysisContent.Clarity(
                clarityScoreAverage,
                aiResponse.clarity().clarityReasons(),
                aiResponse.clarity().notVisibleYet(),
                aiResponse.clarity().nextActions());

        return new MetaAnalysisContent(aiResponse.outer(), aiResponse.inner(), clarity, evidence);
    }

    private int calculateClarityScoreAverage(List<EmotionAnalysisCandidate> selected) {
        return (int) Math.round(selected.stream()
                .mapToInt(EmotionAnalysisCandidate::entryClarityScore)
                .average()
                .orElse(0));
    }

    private String resolveWhySelected(String entryClarityReason) {
        if (entryClarityReason == null || entryClarityReason.isBlank()) {
            return "감정의 흐름과 맥락이 비교적 선명하게 드러나 있어요.";
        }
        return entryClarityReason;
    }

    private String failureReason(Exception e) {
        String reason = e.getMessage();
        if (reason == null || reason.isBlank()) {
            return UNKNOWN_FAILURE_REASON;
        }
        return reason.length() > MAX_FAILURE_REASON_LENGTH ? reason.substring(0, MAX_FAILURE_REASON_LENGTH) : reason;
    }
}
