package com.eunoia.analysis.application;

import com.eunoia.analysis.application.dto.EmotionAnalysisInfo;
import com.eunoia.analysis.application.dto.EmotionScorePoint;
import com.eunoia.analysis.domain.*;
import com.eunoia.common.exception.BusinessException;
import com.eunoia.journal.event.EmotionEntryDeleted;
import com.eunoia.journal.event.EmotionEntryRecorded;
import com.eunoia.journal.query.EmotionEntryQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private static final List<AnalysisStatus> TERMINAL_STATUSES = List.of(AnalysisStatus.SUCCESS, AnalysisStatus.FAILED);

    private final EmotionAnalysisRepository emotionAnalysisRepository;
    private final EmotionAnalysisResultRecorder resultRecorder;
    private final EmotionAnalyzer emotionAnalyzer;
    private final EmotionEntryQueryApi emotionEntryQueryApi;

    // 일기 작성과 같은 트랜잭션에서 실행 — PROCESSING 행이 일기 저장과 함께 커밋된다.
    // MANDATORY: 발행자 트랜잭션이 없으면 조용히 새 트랜잭션을 열지 않고 즉시 실패시킨다.
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void start(EmotionEntryRecorded event) {
        emotionAnalysisRepository.save(
                EmotionAnalysis.start(event.entryId(), event.memberId(), event.entryDate())
        );
    }

    // 바깥 트랜잭션 없이 실행 — 각 조회가 최신 커밋 상태를 봐야 GPT 호출 중 커밋된 삭제를 잡을 수 있다(@ApplicationModuleListener로 안 묶는 이유)
    @Async
    @TransactionalEventListener
    public void handle(EmotionEntryRecorded event) {
        boolean processing = emotionAnalysisRepository.findByEntryId(event.entryId())
                .map(analysis -> analysis.getStatus() == AnalysisStatus.PROCESSING)
                .orElse(false);
        if (!processing) {
            return;
        }
        Optional<String> content = emotionEntryQueryApi.findContent(event.memberId(),  event.entryId());
        if (content.isEmpty()) {
            return;
        }

        analyzeAndRecord(event, content.get());
    }

    // 삭제 구독
    @ApplicationModuleListener
    public void handle(EmotionEntryDeleted event) {
        emotionAnalysisRepository.findByEntryId(event.entryId())
                .ifPresent(EmotionAnalysis::delete);
    }

    private void analyzeAndRecord(EmotionEntryRecorded event, String content) {
        for (int attempt = 1; attempt <= MAX_VALIDATION_ATTEMPTS; attempt++) {
            EmotionAnalysisResult result;
            try {
                result = emotionAnalyzer.analyze(content);
            } catch (RuntimeException e) {
                resultRecorder.recordFailure(event.entryId(), failureReason(e));
                return;
            }

            try {
                // 임시로 '객체만' 생성시켜봐서 도메인 검증을 수행.
                EmotionAnalysis.start(event.entryId(), event.memberId(), event.entryDate()).complete(result);
            } catch (IllegalArgumentException e) {
                if (attempt == MAX_VALIDATION_ATTEMPTS) {
                    resultRecorder.recordFailure(event.entryId(), failureReason(e));
                    return;
                }
                continue;
            }

            resultRecorder.recordSuccess(event.entryId(), result);
            return;
        }
    }

    private String failureReason(Exception e) {
        String reason = e.getMessage();
        if (reason == null || reason.isBlank()) {
            return UNKNOWN_FAILURE_REASON;
        }
        return reason.length() > MAX_FAILURE_REASON_LENGTH ? reason.substring(0, MAX_FAILURE_REASON_LENGTH) : reason;
    }

    @Transactional(readOnly = true)
    public EmotionAnalysisInfo getByEntryId(Long entryId, Long requesterId) {
        EmotionAnalysis analysis = emotionAnalysisRepository.findByEntryId(entryId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "분석 결과를 찾을 수 없어요."));

        if (!analysis.isOwnedBy(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "해당 분석 결과에 대한 접근 권한이 없어요.");
        }

        return EmotionAnalysisInfo.from(analysis);
    }

    @Transactional(readOnly = true)
    public Optional<EmotionAnalysisInfo> getLatest(Long memberId) {
        return emotionAnalysisRepository.findTopByMemberIdAndStatusInOrderByEntryDateDescEntryIdDesc(memberId, TERMINAL_STATUSES)
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
