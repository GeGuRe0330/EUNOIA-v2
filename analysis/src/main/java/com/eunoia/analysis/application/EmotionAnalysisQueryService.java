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
        return emotionAnalysisRepository.findByMemberIdAndEntryDateBetweenAndStatus(
                memberId, startDate, endDate, AnalysisStatus.SUCCESS)
                .stream()
                .map(analysis -> new EmotionAnalysisCandidate(
                        analysis.getEntryId(),
                        analysis.getEntryDate(),
                        analysis.getEntryClarityScore(),
                        analysis.getEntryClarityReason()))
                .toList();
    }
}
