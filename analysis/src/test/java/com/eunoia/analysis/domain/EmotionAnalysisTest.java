package com.eunoia.analysis.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmotionAnalysisTest {

    private static final LocalDate ENTRY_DATE = LocalDate.of(2026, 9, 20);

    @Test
    @DisplayName("entryId가 null이면 생성할 수 없다.")
    void create_withNullEntryId_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(null, 1L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("memberId가 null이면 생성할 수 없다.")
    void create_withNullMemberId_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, null, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("entryDate가 null이면 생성할 수 없다.")
    void create_withNullEntryDate_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, null, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("emotionDetected가 빈 문자열이면 생성할 수 없다.")
    void create_withBlankEmotionDetected_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, ENTRY_DATE, " ", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("emotionScore가 0보다 작으면 생성할 수 없다.")
    void create_withEmotionScoreBelowZero_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", -0.1, 90, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("emotionScore가 100보다 크면 생성할 수 없다.")
    void create_withEmotionScoreAboveHundred_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 100.1, 90, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("entryClarityScore가 0보다 작으면 생성할 수 없다.")
    void create_withEntryClarityScoreBelowZero_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, -1, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("entryClarityScore가 100보다 크면 생성할 수 없다.")
    void create_withEntryClarityScoreAboveHundred_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 101, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("warmMessages가 null이면 생성할 수 없다.")
    void create_withNullWarmMessages_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("warmMessages가 3개가 아니면 생성할 수 없다.")
    void create_withWrongSizeWarmMessages_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("warmMessages에 빈 문자열이 포함되면 생성할 수 없다.")
    void create_withBlankWarmMessage_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", " ", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유효한 값으로 생성하면 모든 필드가 그대로 저장되고 상태는 SUCCESS다.")
    void create_withValidArguments_setAllFields() {
        List<String> warmMessages = List.of("문장1", "문장2", "문장3");

        EmotionAnalysis analysis = EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", warmMessages);

        assertThat(analysis.getEntryId()).isEqualTo(1L);
        assertThat(analysis.getMemberId()).isEqualTo(2L);
        assertThat(analysis.getEntryDate()).isEqualTo(ENTRY_DATE);
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
        assertThat(analysis.getEmotionDetected()).isEqualTo("평온");
        assertThat(analysis.getKeywords()).isEqualTo("평온,안정");
        assertThat(analysis.getInsightSummary()).isEqualTo("요약");
        assertThat(analysis.getFlowHint()).isEqualTo("흐름");
        assertThat(analysis.getEmotionSummary()).isEqualTo("감정요약");
        assertThat(analysis.getEmotionScore()).isEqualTo(80.0);
        assertThat(analysis.getEntryClarityScore()).isEqualTo(90);
        assertThat(analysis.getEntryClarityReason()).isEqualTo("충분함");
        assertThat(analysis.getWarmMessages()).containsExactly("문장1", "문장2", "문장3");
    }

    @Test
    @DisplayName("fail 생성 시 entryId가 null이면 생성할 수 없다.")
    void fail_withNullEntryId_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.fail(null, 2L, ENTRY_DATE, "실패 사유"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fail 생성 시 memberId가 null이면 생성할 수 없다.")
    void fail_withNullMemberId_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.fail(1L, null, ENTRY_DATE, "실패 사유"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fail 생성 시 entryDate가 null이면 생성할 수 없다.")
    void fail_withNullEntryDate_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.fail(1L, 2L, null, "실패 사유"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fail 생성 시 failureReason이 빈 문자열이면 생성할 수 없다.")
    void fail_withBlankFailureReason_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.fail(1L, 2L, ENTRY_DATE, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유효한 값으로 fail 생성하면 상태는 FAILED이고 분석 필드는 비어있다.")
    void fail_withValidArguments_setFailedStatus() {
        EmotionAnalysis analysis = EmotionAnalysis.fail(1L, 2L, ENTRY_DATE, "실패 사유");

        assertThat(analysis.getEntryId()).isEqualTo(1L);
        assertThat(analysis.getMemberId()).isEqualTo(2L);
        assertThat(analysis.getEntryDate()).isEqualTo(ENTRY_DATE);
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getFailureReason()).isEqualTo("실패 사유");
        assertThat(analysis.getEmotionDetected()).isNull();
        assertThat(analysis.getWarmMessages()).isNull();
    }
}
