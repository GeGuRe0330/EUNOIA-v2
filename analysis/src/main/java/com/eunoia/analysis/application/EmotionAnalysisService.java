package com.eunoia.analysis.application;

import com.eunoia.analysis.application.dto.EmotionAnalysisInfo;
import com.eunoia.analysis.application.dto.EmotionScorePoint;
import com.eunoia.analysis.domain.*;
import com.eunoia.common.exception.BusinessException;
import com.eunoia.journal.event.EmotionEntryDeleted;
import com.eunoia.journal.event.EmotionEntryRecorded;
import com.eunoia.journal.query.EmotionEntryQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmotionAnalysisService {

    private static final int MAX_VALIDATION_ATTEMPTS = 3;
    private static final String UNKNOWN_FAILURE_REASON = "원인 불명";
    // EmotionAnalysis.failureReason 컬럼 길이(length = 1000)와 반드시 맞춰야 함
    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private final EmotionAnalysisRepository emotionAnalysisRepository;
    private final EmotionAnalyzer emotionAnalyzer;
    private final EmotionEntryQueryApi emotionEntryQueryApi;

    // 바깥 트랜잭션 없이 실행 — 각 조회가 최신 커밋 상태를 봐야 GPT 호출 중 커밋된 삭제를 잡을 수 있다(@ApplicationModuleListener로 안 묶는 이유)
    @Async
    @TransactionalEventListener
    public void handle(EmotionEntryRecorded event) {
        if (emotionAnalysisRepository.findByEntryId(event.entryId()).isPresent()) {
            return;
        }
        if (!emotionEntryQueryApi.existsEntry(event.memberId(), event.entryId())) {
            return;
        }

        EmotionAnalysis saved = emotionAnalysisRepository.save(analyze(event));

        if (!emotionEntryQueryApi.existsEntry(event.memberId(), event.entryId())) {
            saved.delete();
            emotionAnalysisRepository.save(saved);
        }
    }

    @ApplicationModuleListener
    public void handle(EmotionEntryDeleted event) {
        emotionAnalysisRepository.findByEntryId(event.entryId())
                .ifPresent(EmotionAnalysis::delete);
    }

    private EmotionAnalysis analyze(EmotionEntryRecorded event) {
        for (int attempt = 1; attempt <= MAX_VALIDATION_ATTEMPTS; attempt++) {
            EmotionAnalysisResult result;
            try {
                result = emotionAnalyzer.analyze(event.content());
            } catch (RuntimeException e) {
                return failedAnalysis(event, e);
            }

            try {
                return EmotionAnalysis.create(
                        event.entryId(), event.memberId(), event.entryDate(),
                        result.emotionDetected(), result.keywords(), result.insightSummary(), result.flowHint(),
                        result.emotionSummary(), result.emotionScore(), result.entryClarityScore(),
                        result.entryClarityReason(), result.warmMessages());
            } catch (IllegalArgumentException e) {
                if (attempt == MAX_VALIDATION_ATTEMPTS) {
                    return failedAnalysis(event, e);
                }
            }
        }
        throw new IllegalStateException("도달할 수 없는 상태입니다.");
    }

    private EmotionAnalysis failedAnalysis(EmotionEntryRecorded event, Exception e) {
        String reason = e.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = UNKNOWN_FAILURE_REASON;
        } else if (reason.length() > MAX_FAILURE_REASON_LENGTH) {
            reason = reason.substring(0, MAX_FAILURE_REASON_LENGTH);
        }
        return EmotionAnalysis.fail(event.entryId(), event.memberId(), event.entryDate(), reason);
    }

    @Transactional(readOnly = true)
    public EmotionAnalysisInfo getByEntryId(Long entryId, Long requesterId) {
        EmotionAnalysis analysis = emotionAnalysisRepository.findByEntryId(entryId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "아직 분석 결과가 없어요."));

        if (!analysis.isOwnedBy(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "해당 분석 결과에 대한 접근 권한이 없어요.");
        }

        return EmotionAnalysisInfo.from(analysis);
    }

    @Transactional(readOnly = true)
    public Optional<EmotionAnalysisInfo> getLatest(Long memberId) {
        return emotionAnalysisRepository.findTopByMemberIdOrderByEntryDateDescEntryIdDesc(memberId)
                .map(EmotionAnalysisInfo::from);
    }

    @Transactional(readOnly = true)
    public List<EmotionScorePoint> getScores(Long memberId) {
        List<EmotionAnalysis> analyses = emotionAnalysisRepository
                .findTop7ByMemberIdAndStatusOrderByEntryDateDescEntryIdDesc(memberId, AnalysisStatus.SUCCESS);
        Collections.reverse(analyses);
        return analyses.stream().map(EmotionScorePoint::from).toList();
    }
}
