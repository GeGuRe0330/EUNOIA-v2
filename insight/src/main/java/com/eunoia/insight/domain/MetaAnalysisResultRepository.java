package com.eunoia.insight.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MetaAnalysisResultRepository {
    MetaAnalysisResult save(MetaAnalysisResult result);

    Optional<MetaAnalysisResult> findLatestSuccessByMemberId(Long memberId);

    List<MetaAnalysisResult> findAllSuccessByMemberId(Long memberId);

    Optional<MetaAnalysisResult> findLatestAttemptOfDay(Long memberId, LocalDate periodEnd);

    Optional<MetaAnalysisResult> findById(Long id);

    Optional<MetaAnalysisResult> findByIdForUpdate(Long id);

    // 생성 시각이 threshold 이전인데 아직 PROCESSING인 행을 FAILED로 바꾸고 바꾼 건수를 돌려준다(조건부 일괄 UPDATE)
    int failProcessingCreatedBefore(LocalDateTime threshold, String failureReason);
}
