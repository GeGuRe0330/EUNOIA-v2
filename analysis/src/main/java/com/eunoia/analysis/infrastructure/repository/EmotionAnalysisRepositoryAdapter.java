package com.eunoia.analysis.infrastructure.repository;

import com.eunoia.analysis.domain.AnalysisStatus;
import com.eunoia.analysis.domain.EmotionAnalysis;
import com.eunoia.analysis.domain.EmotionAnalysisRepository;
import com.eunoia.analysis.domain.EntryDateAverageScore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmotionAnalysisRepositoryAdapter implements EmotionAnalysisRepository {

    private final EmotionAnalysisJpaRepository jpaRepository;

    @Override
    public EmotionAnalysis save(EmotionAnalysis analysis) {
        return jpaRepository.save(analysis);
    }

    @Override
    public Optional<EmotionAnalysis> findByEntryId(Long entryId) {
        return jpaRepository.findByEntryId(entryId);
    }

    @Override
    public List<EmotionAnalysis> findByMemberIdAndEntryDateBetweenAndStatusOrderByEntryDateAscEntryIdAsc(
            Long memberId, LocalDate startDate, LocalDate endDate, AnalysisStatus status) {
        return jpaRepository.findByMemberIdAndEntryDateBetweenAndStatusOrderByEntryDateAscEntryIdAsc(memberId, startDate, endDate, status);
    }

    @Override
    public Optional<EmotionAnalysis> findTopByMemberIdAndStatusInOrderByEntryDateDescEntryIdDesc(Long memberId, List<AnalysisStatus> statuses) {
        return jpaRepository.findTopByMemberIdAndStatusInOrderByEntryDateDescEntryIdDesc(memberId, statuses);
    }

    @Override
    public Optional<EmotionAnalysis> findByEntryIdForUpdate(Long entryId) {
        return jpaRepository.findByEntryIdForUpdate(entryId);
    }

    @Override
    public int failProcessingCreatedBefore(LocalDateTime threshold, String failureReason) {
        return jpaRepository.failProcessingCreatedBefore(
                AnalysisStatus.PROCESSING, AnalysisStatus.FAILED, failureReason, threshold, LocalDateTime.now()
        );
    }

    @Override
    public List<EmotionAnalysis> findTop7ByMemberIdAndStatusOrderByEntryDateDescEntryIdDesc(Long memberId, AnalysisStatus status) {
        return jpaRepository.findTop7ByMemberIdAndStatusOrderByEntryDateDescEntryIdDesc(memberId, status);
    }

    @Override
    public List<EmotionAnalysis> findByMemberIdAndEntryIdInAndStatus(Long memberId, List<Long> entryIds, AnalysisStatus status) {
        return jpaRepository.findByMemberIdAndEntryIdInAndStatus(memberId, entryIds, status);
    }

    @Override
    public List<EntryDateAverageScore> averageScoreDailyByMemberIdAndPeriod(Long memberId, AnalysisStatus status, LocalDate from, LocalDate to) {
        return jpaRepository.averageScoreDailyByMemberIdAndPeriod(memberId, status, from, to);
    }
}
