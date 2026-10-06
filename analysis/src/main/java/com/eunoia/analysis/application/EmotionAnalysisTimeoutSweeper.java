package com.eunoia.analysis.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionAnalysisTimeoutSweeper {

    // 분석 1회 최악 ≈60초(spring-ai timeout 30s × 2회 시도) × 검증 재시도 3회 ≈ 3분보다 길게
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(5);

    private final EmotionAnalysisResultRecorder resultRecorder;

    // 1분마다 실행(이전 실행이 끝난 뒤 대기) — 이벤트 유실·재시작으로 영영 PROCESSING인 분석을 FAILED로 확정한다
    @Scheduled(fixedDelay = 60_000)
    public void failStuckAnalyses() {
        int failed = resultRecorder.failStuck(PROCESSING_TIMEOUT);
        if (failed > 0) {
            log.warn("처리 시간 초과로 FAILED 전환한 분석 {}건(생성 후 {}분 경과)", failed, PROCESSING_TIMEOUT.toMinutes());
        }
    }
}
