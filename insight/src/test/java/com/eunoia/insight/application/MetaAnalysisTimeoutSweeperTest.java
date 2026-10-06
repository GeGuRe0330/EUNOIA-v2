package com.eunoia.insight.application;

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
class MetaAnalysisTimeoutSweeperTest {

    @Mock
    private MetaAnalysisResultRecorder resultRecorder;

    private MetaAnalysisTimeoutSweeper sweeper;

    @BeforeEach
    void setUp() {
        sweeper = new MetaAnalysisTimeoutSweeper(resultRecorder);
    }

    @Test
    @DisplayName("실행하면 3분 기준으로 멈춘 시도를 FAILED로 확정하도록 기록기에 맡긴다.")
    void failStuckGenerations_delegatesWithThreeMinuteTimeout() {
        when(resultRecorder.failStuck(Duration.ofMinutes(3))).thenReturn(2);

        sweeper.failStuckGenerations();

        verify(resultRecorder).failStuck(Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("멈춘 시도가 없어도(0건) 정상 종료한다.")
    void failStuckGenerations_withNothingStuck_completes() {
        when(resultRecorder.failStuck(Duration.ofMinutes(3))).thenReturn(0);

        sweeper.failStuckGenerations();

        verify(resultRecorder).failStuck(Duration.ofMinutes(3));
    }
}
