package com.eunoia.analysis.application;

import com.eunoia.analysis.domain.AnalysisStatus;
import com.eunoia.analysis.domain.EmotionAnalysis;
import com.eunoia.analysis.domain.EmotionAnalysisRepository;
import com.eunoia.analysis.query.EmotionAnalysisCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmotionAnalysisQueryServiceTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 9, 30);

    @Mock
    private EmotionAnalysisRepository emotionAnalysisRepository;

    private EmotionAnalysisQueryService emotionAnalysisQueryService;

    @BeforeEach
    void setUp() {
        emotionAnalysisQueryService = new EmotionAnalysisQueryService(emotionAnalysisRepository);
    }

    @Test
    @DisplayName("SUCCESS 상태로 리포지토리에 조회하고, 결과를 선별용 최소 필드(Candidate)로 매핑한다.")
    void findSuccessfulAnalyses_withSuccessAnalyses_mapsToCandidates() {
        EmotionAnalysis analysis = EmotionAnalysis.create(1L, 2L, LocalDate.of(2026, 9, 20), "평온", "평온,안정",
                "요약", "흐름", "감정요약", 80.0, 90, "충분함", List.of("문장1", "문장2", "문장3"));

        when(emotionAnalysisRepository.findByMemberIdAndEntryDateBetweenAndStatusOrderByEntryDateAscEntryIdAsc(
                2L, START_DATE, END_DATE, AnalysisStatus.SUCCESS))
                .thenReturn(List.of(analysis));

        List<EmotionAnalysisCandidate> result =
                emotionAnalysisQueryService.findSuccessfulAnalyses(2L, START_DATE, END_DATE);

        assertThat(result).hasSize(1);
        EmotionAnalysisCandidate candidate = result.get(0);
        assertThat(candidate.entryId()).isEqualTo(1L);
        assertThat(candidate.entryDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(candidate.entryClarityScore()).isEqualTo(90);
        assertThat(candidate.entryClarityReason()).isEqualTo("충분함");
    }

    @Test
    @DisplayName("해당 기간에 SUCCESS 분석이 없으면 빈 리스트를 반환한다.")
    void findSuccessfulAnalyses_withNoResults_returnsEmptyList() {
        when(emotionAnalysisRepository.findByMemberIdAndEntryDateBetweenAndStatusOrderByEntryDateAscEntryIdAsc(
                2L, START_DATE, END_DATE, AnalysisStatus.SUCCESS))
                .thenReturn(List.of());

        List<EmotionAnalysisCandidate> result =
                emotionAnalysisQueryService.findSuccessfulAnalyses(2L, START_DATE, END_DATE);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("memberId가 null이면 조회할 수 없다.")
    void findSuccessfulAnalyses_withNullMemberId_throws() {
        assertThatThrownBy(() -> emotionAnalysisQueryService.findSuccessfulAnalyses(null, START_DATE, END_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("startDate가 null이면 조회할 수 없다.")
    void findSuccessfulAnalyses_withNullStartDate_throws() {
        assertThatThrownBy(() -> emotionAnalysisQueryService.findSuccessfulAnalyses(2L, null, END_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("endDate가 null이면 조회할 수 없다.")
    void findSuccessfulAnalyses_withNullEndDate_throws() {
        assertThatThrownBy(() -> emotionAnalysisQueryService.findSuccessfulAnalyses(2L, START_DATE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("startDate가 endDate보다 이후면 조회할 수 없다.")
    void findSuccessfulAnalyses_withStartDateAfterEndDate_throws() {
        assertThatThrownBy(() -> emotionAnalysisQueryService.findSuccessfulAnalyses(2L, END_DATE, START_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
