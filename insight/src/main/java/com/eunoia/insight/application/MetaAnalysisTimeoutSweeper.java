package com.eunoia.insight.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetaAnalysisTimeoutSweeper {

    // 메타분석 GPT 호출 최악 ≈60초(spring-ai timeout 30s × 2회 시도), 검증 재시도 루프 없음 — 그보다 충분히 길게
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(3);

    private final MetaAnalysisResultRecorder resultRecorder;

    // 1분마다 실행(이전 실행이 끝난 뒤 대기) — 이벤트 유실·재시작으로 영영 PROCESSING인 시도를 FAILED로 확정해
    // 사용자가 다시 요청(새 시도)할 수 있게 한다
    @Scheduled(fixedDelay = 60_000)
    public void failStuckGenerations() {
        int failed = resultRecorder.failStuck(PROCESSING_TIMEOUT);
        if (failed > 0) {
            log.warn("처리 시간 초과로 FAILED 전환한 메타분석 시도 {}건(생성 후 {}분 경과)", failed, PROCESSING_TIMEOUT.toMinutes());
        }
    }
}
