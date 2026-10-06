package com.eunoia.analysis.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Test
    @DisplayName("새로 생성한 분석은 성공·실패 모두 삭제되지 않은 상태다.")
    void createAndFail_newAnalysis_isNotDeleted() {
        EmotionAnalysis success = EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));
        EmotionAnalysis failed = EmotionAnalysis.fail(1L, 2L, ENTRY_DATE, "실패 사유");

        assertThat(success.getDeletedAt()).isNull();
        assertThat(failed.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("성공한 분석을 삭제하면 삭제 시각이 기록된다.")
    void delete_successAnalysis_setDeletedAt() {
        EmotionAnalysis analysis = EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));
        LocalDateTime before = LocalDateTime.now();

        analysis.delete();

        assertThat(analysis.getDeletedAt()).isBetween(before, LocalDateTime.now());
    }

    @Test
    @DisplayName("실패한 분석도 삭제할 수 있고, 상태는 FAILED로 유지된다.")
    void delete_failedAnalysis_setDeletedAtAndKeepStatus() {
        EmotionAnalysis analysis = EmotionAnalysis.fail(1L, 2L, ENTRY_DATE, "실패 사유");
        LocalDateTime before = LocalDateTime.now();

        analysis.delete();

        assertThat(analysis.getDeletedAt()).isBetween(before, LocalDateTime.now());
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    @DisplayName("start 생성 시 entryId가 null이면 생성할 수 없다.")
    void start_withNullEntryId_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.start(null, 2L, ENTRY_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start 생성 시 memberId가 null이면 생성할 수 없다.")
    void start_withNullMemberId_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.start(1L, null, ENTRY_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start 생성 시 entryDate가 null이면 생성할 수 없다.")
    void start_withNullEntryDate_throws() {
        assertThatThrownBy(() -> EmotionAnalysis.start(1L, 2L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start로 생성하면 상태는 PROCESSING이고 결과·실패 사유 필드는 비어있다.")
    void start_withValidArguments_setProcessingStatus() {
        EmotionAnalysis analysis = EmotionAnalysis.start(1L, 2L, ENTRY_DATE);

        assertThat(analysis.getEntryId()).isEqualTo(1L);
        assertThat(analysis.getMemberId()).isEqualTo(2L);
        assertThat(analysis.getEntryDate()).isEqualTo(ENTRY_DATE);
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
        assertThat(analysis.getEmotionDetected()).isNull();
        assertThat(analysis.getEmotionScore()).isNull();
        assertThat(analysis.getWarmMessages()).isNull();
        assertThat(analysis.getFailureReason()).isNull();
        assertThat(analysis.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("처리 중인 분석을 complete하면 상태는 SUCCESS이고 모든 필드가 그대로 저장된다.")
    void complete_processingAnalysis_setSuccessAndAllFields() {
        EmotionAnalysis analysis = EmotionAnalysis.start(1L, 2L, ENTRY_DATE);

        analysis.complete("평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));

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
    @DisplayName("complete 검증에 실패하면 예외가 나고, 상태와 필드는 바뀌지 않아 다시 시도할 수 있다.")
    void complete_withInvalidResult_throwsAndKeepsProcessing() {
        EmotionAnalysis analysis = EmotionAnalysis.start(1L, 2L, ENTRY_DATE);
        List<String> warmMessages = List.of("문장1", "문장2", "문장3");

        assertThatThrownBy(() -> analysis.complete(" ", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함", warmMessages))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analysis.complete("평온", "평온,안정", "요약", "흐름", "감정요약", 100.1, 90, "충분함", warmMessages))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analysis.complete("평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, -1, "충분함", warmMessages))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> analysis.complete("평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
        assertThat(analysis.getEmotionDetected()).isNull();
        assertThat(analysis.getEmotionScore()).isNull();
        assertThat(analysis.getWarmMessages()).isNull();

        analysis.complete("평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함", warmMessages);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
    }

    @Test
    @DisplayName("처리 중인 분석을 markFailed하면 상태는 FAILED이고 실패 사유가 저장되며 분석 필드는 비어있다.")
    void markFailed_processingAnalysis_setFailedStatusAndReason() {
        EmotionAnalysis analysis = EmotionAnalysis.start(1L, 2L, ENTRY_DATE);

        analysis.markFailed("실패 사유");

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getFailureReason()).isEqualTo("실패 사유");
        assertThat(analysis.getEmotionDetected()).isNull();
        assertThat(analysis.getWarmMessages()).isNull();
    }

    @Test
    @DisplayName("markFailed 시 failureReason이 빈 문자열이면 예외가 나고 상태는 PROCESSING으로 유지된다.")
    void markFailed_withBlankFailureReason_throwsAndKeepsProcessing() {
        EmotionAnalysis analysis = EmotionAnalysis.start(1L, 2L, ENTRY_DATE);

        assertThatThrownBy(() -> analysis.markFailed(" "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
        assertThat(analysis.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("이미 성공한 분석은 다시 complete하거나 markFailed할 수 없다(종결 상태).")
    void transition_fromSuccess_throwsIllegalState() {
        EmotionAnalysis analysis = EmotionAnalysis.create(1L, 2L, ENTRY_DATE, "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));

        assertThatThrownBy(() -> analysis.complete("불안", "불안", "요약2", "흐름2", "감정요약2", 10.0, 20, "이유",
                List.of("a", "b", "c")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> analysis.markFailed("실패 사유"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
        assertThat(analysis.getEmotionDetected()).isEqualTo("평온");
    }

    @Test
    @DisplayName("이미 실패한 분석은 다시 complete하거나 markFailed할 수 없다(종결 상태, 늦게 도착한 결과를 버린다).")
    void transition_fromFailed_throwsIllegalState() {
        EmotionAnalysis analysis = EmotionAnalysis.fail(1L, 2L, ENTRY_DATE, "실패 사유");

        assertThatThrownBy(() -> analysis.complete("평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> analysis.markFailed("다른 사유"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getFailureReason()).isEqualTo("실패 사유");
        assertThat(analysis.getEmotionDetected()).isNull();
    }

    @Test
    @DisplayName("처리 중인 분석도 삭제할 수 있고, 상태는 PROCESSING으로 유지된다.")
    void delete_processingAnalysis_setDeletedAtAndKeepStatus() {
        EmotionAnalysis analysis = EmotionAnalysis.start(1L, 2L, ENTRY_DATE);
        LocalDateTime before = LocalDateTime.now();

        analysis.delete();

        assertThat(analysis.getDeletedAt()).isBetween(before, LocalDateTime.now());
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
    }
}
