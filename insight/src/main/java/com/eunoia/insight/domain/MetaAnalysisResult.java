package com.eunoia.insight.domain;

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
@Table(name = "meta_analysis_results",  uniqueConstraints = @UniqueConstraint(
        name = "uq_meta_analysis_member_period_end_attempt", columnNames = {"member_id", "period_end", "attempt_no"}),
        indexes = @Index(name = "idx_meta_analysis_generation_status_created_at", columnList = "generation_status, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetaAnalysisResult extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    private Integer basedOnCount;

    @Column(nullable = false)
    private Integer excludedEntryCount;

    @JdbcTypeCode(SqlTypes.JSON)
    private MetaAnalysisContent content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetaAnalysisGenerationStatus generationStatus;

    @Column(nullable = false)
    private Integer attemptNo;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Long> selectedEntryIds;

    @Column(length = 1000)
    private String failureReason;

    private MetaAnalysisResult(Long memberId, LocalDate periodStart, LocalDate periodEnd,
                               Integer basedOnCount, Integer excludedEntryCount, MetaAnalysisContent content) {
        validateMemberId(memberId);
        validatePeriod(periodStart, periodEnd);
        validateCounts(basedOnCount, excludedEntryCount);
        validateContent(content);

        this.memberId = memberId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.basedOnCount = basedOnCount;
        this.excludedEntryCount = excludedEntryCount;
        this.content = content;
        this.generationStatus = MetaAnalysisGenerationStatus.SUCCESS;
        this.attemptNo = 1;
    }

    public static MetaAnalysisResult create(Long memberId, LocalDate periodStart, LocalDate periodEnd,
                                            Integer basedOnCount, Integer excludedEntryCount, MetaAnalysisContent content) {
        return new MetaAnalysisResult(memberId, periodStart, periodEnd, basedOnCount, excludedEntryCount, content);
    }

    private MetaAnalysisResult(Long memberId, LocalDate periodStart, LocalDate periodEnd, Integer attemptNo,
                               Integer basedOnCount, Integer excludedEntryCount, List<Long> selectedEntryIds) {
        validateMemberId(memberId);
        validatePeriod(periodStart, periodEnd);
        validateAttemptNo(attemptNo);
        validateCounts(basedOnCount, excludedEntryCount);
        validateSelectedEntryIds(selectedEntryIds);

        this.memberId = memberId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.attemptNo = attemptNo;
        this.basedOnCount = basedOnCount;
        this.excludedEntryCount = excludedEntryCount;
        this.selectedEntryIds = selectedEntryIds;
        this.generationStatus = MetaAnalysisGenerationStatus.PROCESSING;
    }

    public static MetaAnalysisResult start(Long memberId, LocalDate periodStart, LocalDate periodEnd, Integer attemptNo,
                                           Integer basedOnCount, Integer excludedEntryCount, List<Long> selectedEntryIds) {
        return new MetaAnalysisResult(memberId, periodStart, periodEnd, attemptNo,
                basedOnCount, excludedEntryCount, selectedEntryIds);
    }

    public void complete(MetaAnalysisContent content) {
        validateProcessing();
        validateContent(content);

        this.generationStatus = MetaAnalysisGenerationStatus.SUCCESS;
        this.content = content;
    }

    public void markFailed(String failureReason) {
        validateProcessing();
        validateFailureReason(failureReason);

        this.generationStatus = MetaAnalysisGenerationStatus.FAILED;
        this.failureReason = failureReason;
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId는 필수입니다.");
        }
    }

    private void validatePeriod(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("분석 기간은 필수입니다.");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("periodStart는 periodEnd보다 이후일 수 없습니다.");
        }
    }

    private void validateContent(MetaAnalysisContent content) {
        if (content == null) {
            throw new IllegalArgumentException("content는 필수입니다.");
        }
    }

    private void validateCounts(Integer basedOnCount, Integer excludedEntryCount) {
        if (basedOnCount == null || basedOnCount < 0) {
            throw new IllegalArgumentException("basedOnCount는 0 이상이어야 합니다.");
        }
        if (excludedEntryCount == null || excludedEntryCount < 0) {
            throw new IllegalArgumentException("excludedEntryCount는 0 이상이어야 합니다.");
        }
    }

    private void validateAttemptNo(Integer attemptNo) {
        if (attemptNo == null || attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo는 1 이상이어야 합니다.");
        }
    }

    private void validateSelectedEntryIds(List<Long> selectedEntryIds) {
        if (selectedEntryIds == null || selectedEntryIds.isEmpty()) {
            throw new IllegalArgumentException("selectedEntryIds는 비어 있을 수 없습니다.");
        }
    }

    private void validateFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason은 필수입니다.");
        }
    }

    private void validateProcessing() {
        if (this.generationStatus != MetaAnalysisGenerationStatus.PROCESSING) {
            throw new IllegalStateException("처리 중인 분석만 완료/실패로 바꿀 수 있습니다.");
        }
    }
}
