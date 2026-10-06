package com.eunoia.analysis.application;

import com.eunoia.analysis.domain.AnalysisStatus;
import com.eunoia.analysis.domain.EmotionAnalysis;
import com.eunoia.analysis.domain.EmotionAnalysisRepository;
import com.eunoia.analysis.domain.EmotionAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmotionAnalysisResultRecorder {

    private final EmotionAnalysisRepository emotionAnalysisRepository;
    private static final String TIMEOUT_FAILURE_REASON = "처리 시간 초과";

    @Transactional
    public void recordSuccess(Long entryId, EmotionAnalysisResult result) {
        findProcessing(entryId).ifPresent(analysis -> analysis.complete(result));
    }

    @Transactional
    public void recordFailure(Long entryId, String failureReason) {
        findProcessing(entryId).ifPresent(analysis -> analysis.markFailed(failureReason));
    }

    // 조건부 UPDATE라 이미 SUCCESS/FAILED로 전이된 행은 건드리지 않는다
    @Transactional
    public int failStuck(Duration timeout) {
        return emotionAnalysisRepository.failProcessingCreatedBefore(
                LocalDateTime.now().minus(timeout), TIMEOUT_FAILURE_REASON
        );
    }

    // FOR UPDATE — sweeper의 FAILED 전환과 겹쳐도 먼저 잠근 쪽이 이기고, 나중 쪽은 최신 상태를 보고 건너뛴다
    private Optional<EmotionAnalysis> findProcessing(Long entryId) {
        return emotionAnalysisRepository.findByEntryIdForUpdate(entryId)
                .filter(analysis -> analysis.getStatus() == AnalysisStatus.PROCESSING);
    }
}
