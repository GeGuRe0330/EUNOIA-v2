package com.eunoia.insight.application;

import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisGenerationStatus;
import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

// GPT 호출이 끝난 뒤 짧은 트랜잭션에서 행을 다시 읽어 전이한다. 이미 종결 상태면 늦게 도착한 결과를 버린다.
@Service
@RequiredArgsConstructor
public class MetaAnalysisResultRecorder {

    private final MetaAnalysisResultRepository metaAnalysisResultRepository;

    private static final String TIMEOUT_FAILURE_REASON = "처리 시간 초과";

    @Transactional
    public void recordSuccess(Long resultId, MetaAnalysisContent content) {
        findProcessing(resultId).ifPresent(result -> result.complete(content));
    }

    @Transactional
    public void recordFailure(Long resultId, String failureReason) {
        findProcessing(resultId).ifPresent(result -> result.markFailed(failureReason));
    }

    // 생성 후 timeout이 지나도 PROCESSING인 시도를 FAILED로 확정한다(이벤트 유실·서버 재시작으로 멈춘 건).
    // 조건부 UPDATE라 이미 SUCCESS/FAILED로 전이된 행은 건드리지 않는다.
    @Transactional
    public int failStuck(Duration timeout) {
        return metaAnalysisResultRepository.failProcessingCreatedBefore(
                LocalDateTime.now().minus(timeout), TIMEOUT_FAILURE_REASON
        );
    }

    // FOR UPDATE — 나중에 추가할 sweeper의 FAILED 전환과 겹쳐도 먼저 잠근 쪽이 이기고, 나중 쪽은 최신 상태를 보고 건너뛴다
    private Optional<MetaAnalysisResult> findProcessing(Long resultId) {
        return metaAnalysisResultRepository.findByIdForUpdate(resultId)
                .filter(result -> result.getGenerationStatus() == MetaAnalysisGenerationStatus.PROCESSING);
    }
}
