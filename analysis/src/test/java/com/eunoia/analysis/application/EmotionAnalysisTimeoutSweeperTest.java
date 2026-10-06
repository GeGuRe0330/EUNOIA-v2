package com.eunoia.analysis.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmotionAnalysisTimeoutSweeperTest {

    @Mock
    private EmotionAnalysisResultRecorder resultRecorder;

    private EmotionAnalysisTimeoutSweeper sweeper;

    @BeforeEach
    void setUp() {
        sweeper = new EmotionAnalysisTimeoutSweeper(resultRecorder);
    }

    @Test
    @DisplayName("실행하면 5분 기준으로 멈춘 분석을 FAILED로 확정하도록 기록기에 맡긴다.")
    void failStuckAnalyses_delegatesWithFiveMinuteTimeout() {
        when(resultRecorder.failStuck(Duration.ofMinutes(5))).thenReturn(3);

        sweeper.failStuckAnalyses();

        verify(resultRecorder).failStuck(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("멈춘 분석이 없어도(0건) 정상 종료한다.")
    void failStuckAnalyses_withNothingStuck_completes() {
        when(resultRecorder.failStuck(Duration.ofMinutes(5))).thenReturn(0);

        sweeper.failStuckAnalyses();

        verify(resultRecorder).failStuck(Duration.ofMinutes(5));
    }
}
