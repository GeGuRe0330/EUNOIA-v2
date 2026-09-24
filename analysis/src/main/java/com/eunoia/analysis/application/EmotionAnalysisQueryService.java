package com.eunoia.analysis.application;

import com.eunoia.analysis.domain.AnalysisStatus;
import com.eunoia.analysis.domain.EmotionAnalysisRepository;
import com.eunoia.analysis.query.EmotionAnalysisCandidate;
import com.eunoia.analysis.query.EmotionAnalysisQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmotionAnalysisQueryService implements EmotionAnalysisQueryApi {

    private final EmotionAnalysisRepository emotionAnalysisRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EmotionAnalysisCandidate> findSuccessfulAnalyses(Long memberId, LocalDate startDate, LocalDate endDate) {
        validateQueryParameters(memberId, startDate, endDate);

        return emotionAnalysisRepository.findByMemberIdAndEntryDateBetweenAndStatusOrderByEntryDateAscEntryIdAsc(
                memberId, startDate, endDate, AnalysisStatus.SUCCESS)
                .stream()
                .map(analysis -> new EmotionAnalysisCandidate(
                        analysis.getEntryId(),
                        analysis.getEntryDate(),
                        analysis.getEntryClarityScore(),
                        analysis.getEntryClarityReason()))
                .toList();
    }

    private void validateQueryParameters(Long memberId, LocalDate startDate, LocalDate endDate) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId는 필수입니다.");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("조회 기간은 필수입니다.");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate는 endDate보다 이후일 수 없습니다.");
        }
    }
}
