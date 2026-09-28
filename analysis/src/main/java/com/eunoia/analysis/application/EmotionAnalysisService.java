package com.eunoia.analysis.application;

import com.eunoia.analysis.application.dto.EmotionAnalysisInfo;
import com.eunoia.analysis.application.dto.EmotionScorePoint;
import com.eunoia.analysis.domain.*;
import com.eunoia.common.exception.BusinessException;
import com.eunoia.journal.event.EmotionEntryRecorded;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @ApplicationModuleListener
    public void handle(EmotionEntryRecorded event) {
        if (emotionAnalysisRepository.findByEntryId(event.entryId()).isPresent()) {
            return;
        }

        emotionAnalysisRepository.save(analyze(event));
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
