package com.eunoia.analysis.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmotionAnalysisTest {

    @Test
    @DisplayName("entryId가 null이면 생성할 수 없다.")
    void create_withNullEntryId_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(null, 1L, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("memberId가 null이면 생성할 수 없다.")
    void create_withNullMemberId_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, null, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("emotionDetected가 빈 문자열이면 생성할 수 없다.")
    void create_withBlankEmotionDetected_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, " ", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("emotionScore가 0보다 작으면 생성할 수 없다.")
    void create_withEmotionScoreBelowZero_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, "평온", "평온,안정",
                "요약", "흐름", "감정요약", -0.1, 90, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("emotionScore가 100보다 크면 생성할 수 없다.")
    void create_withEmotionScoreAboveHundred_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 100.1, 90, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("entryClarityScore가 0보다 작으면 생성할 수 없다.")
    void create_withEntryClarityScoreBelowZero_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, -1, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("entryClarityScore가 100보다 크면 생성할 수 없다.")
    void create_withEntryClarityScoreAboveHundred_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 101, "충분함", List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("warmMessages가 null이면 생성할 수 없다.")
    void create_withNullWarmMessages_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("warmMessages가 3개가 아니면 생성할 수 없다.")
    void create_withWrongSizeWarmMessages_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("warmMessages에 빈 문자열이 포함되면 생성할 수 없다.")
    void create_withBlankWarmMessage_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.create(1L, 2L, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", " ", "문장3")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유효한 값으로 생성하면 모든 필드가 그대로 저장된다.")
    void create_withValidArguments_setAllFields() {
        List<String> warmMessages = List.of("문장1", "문장2", "문장3");

        EmotionAnalysis analysis = EmotionAnalysis.create(1L, 2L, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", warmMessages);

        assertThat(analysis.getEntryId()).isEqualTo(1L);
        assertThat(analysis.getMemberId()).isEqualTo(2L);
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
}
