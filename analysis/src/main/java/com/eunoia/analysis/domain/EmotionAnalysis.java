package com.eunoia.analysis.domain;

import com.eunoia.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "emotion_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmotionAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long entryId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    private String emotionDetected;

    private String keywords;

    @Lob
    private String insightSummary;

    @Lob
    private String flowHint;

    @Lob
    private String emotionSummary;

    private Double emotionScore;

    private Integer entryClarityScore;

    @Column(length = 4000)
    private String entryClarityReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warm_messages")
    private List<String> warmMessages;

    @Column(length = 1000)
    private String failureReason;

    private EmotionAnalysis(Long entryId, Long memberId, LocalDate entryDate, String emotionDetected, String keywords,
                            String insightSummary, String flowHint, String emotionSummary,
                            Double emotionScore, Integer entryClarityScore, String entryClarityReason,
                            List<String> warmMessages) {
        validateEntryId(entryId);
        validateMemberId(memberId);
        validateEntryDate(entryDate);
        validateEmotionDetected(emotionDetected);
        validateEmotionScore(emotionScore);
        validateEntryClarityScore(entryClarityScore);
        validateWarmMessages(warmMessages);

        this.entryId = entryId;
        this.memberId = memberId;
        this.entryDate = entryDate;
        this.status = AnalysisStatus.SUCCESS;
        this.emotionDetected = emotionDetected;
        this.keywords = keywords;
        this.insightSummary = insightSummary;
        this.flowHint = flowHint;
        this.emotionSummary = emotionSummary;
        this.emotionScore = emotionScore;
        this.entryClarityScore = entryClarityScore;
        this.entryClarityReason = entryClarityReason;
        this.warmMessages = warmMessages;
    }

    private EmotionAnalysis(Long entryId, Long memberId, LocalDate entryDate, String failureReason) {
        validateEntryId(entryId);
        validateMemberId(memberId);
        validateEntryDate(entryDate);
        validateFailureReason(failureReason);

        this.entryId = entryId;
        this.memberId = memberId;
        this.entryDate = entryDate;
        this.status = AnalysisStatus.FAILED;
        this.failureReason = failureReason;
    }

    public static EmotionAnalysis create(Long entryId, Long memberId, LocalDate entryDate, String emotionDetected, String keywords,
                                         String insightSummary, String flowHint, String emotionSummary,
                                         Double emotionScore, Integer entryClarityScore, String entryClarityReason,
                                         List<String> warmMessages) {

        return new EmotionAnalysis(entryId, memberId, entryDate, emotionDetected, keywords, insightSummary, flowHint,
                emotionSummary, emotionScore, entryClarityScore, entryClarityReason, warmMessages);
    }

    public static EmotionAnalysis fail(Long entryId, Long memberId, LocalDate entryDate, String failureReason) {
        return new  EmotionAnalysis(entryId, memberId, entryDate, failureReason);
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId는 필수입니다.");
        }
    }

    private void validateEntryId(Long entryId) {
        if (entryId == null) {
            throw new IllegalArgumentException("entryId는 필수입니다.");
        }
    }

    private void validateEntryDate(LocalDate entryDate) {
        if (entryDate == null) {
            throw new IllegalArgumentException("entryDate는 필수입니다.");
        }
    }

    private void validateFailureReason(String failureReason) {
        if (failureReason == null ||  failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 필수입니다.");
        }
    }

    private void validateEmotionDetected(String emotionDetected) {
        if (emotionDetected == null || emotionDetected.isBlank()) {
            throw new IllegalArgumentException("emotionDetected는 필수입니다.");
        }
    }

    private void validateEmotionScore(Double emotionScore) {
        if (emotionScore == null || emotionScore < 0 || emotionScore > 100) {
            throw new IllegalArgumentException("emotionScore는 0~100 사이여야 합니다.");
        }
    }

    private void validateEntryClarityScore(Integer entryClarityScore) {
        if (entryClarityScore == null || entryClarityScore < 0 || entryClarityScore > 100) {
            throw new IllegalArgumentException("entryClarityScore는 0~100 사이여야 합니다.");
        }
    }

    private void validateWarmMessages(List<String> warmMessages) {
        if (warmMessages == null || warmMessages.size() != 3) {
            throw new IllegalArgumentException("warmMessages는 3개여야 합니다.");
        }
        if (warmMessages.stream().anyMatch(message -> message == null || message.isBlank())) {
            throw new IllegalArgumentException("warmMessages는 빈 문자열을 포함할 수 없습니다.");
        }
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
