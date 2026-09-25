package com.eunoia.insight.domain;

import com.eunoia.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Entity
@Table(name = "meta_analysis_results",  uniqueConstraints = @UniqueConstraint(
        name = "uq_meta_analysis_member_period_end", columnNames = {"member_id", "period_end"}
))
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
    @Column(nullable = false)
    private MetaAnalysisContent content;

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
    }

    public static MetaAnalysisResult create(Long memberId, LocalDate periodStart, LocalDate periodEnd,
                                            Integer basedOnCount, Integer excludedEntryCount, MetaAnalysisContent content) {
        return new MetaAnalysisResult(memberId, periodStart, periodEnd, basedOnCount, excludedEntryCount, content);
    }

    public void update(Integer basedOnCount, Integer excludedEntryCount, MetaAnalysisContent content) {
        validateCounts(basedOnCount, excludedEntryCount);
        validateContent(content);

        this.basedOnCount = basedOnCount;
        this.excludedEntryCount = excludedEntryCount;
        this.content = content;
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
}
