package com.eunoia.analysis.application;

import com.eunoia.analysis.application.dto.EmotionAnalysisInfo;
import com.eunoia.analysis.domain.EmotionAnalysis;
import com.eunoia.analysis.domain.EmotionAnalysisRepository;
import com.eunoia.analysis.domain.EmotionAnalysisResult;
import com.eunoia.analysis.domain.EmotionAnalyzer;
import com.eunoia.common.exception.BusinessException;
import com.eunoia.journal.event.EmotionEntryRecorded;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmotionAnalysisService {

    private final EmotionAnalysisRepository emotionAnalysisRepository;
    private final EmotionAnalyzer emotionAnalyzer;

    @ApplicationModuleListener
    public void handle(EmotionEntryRecorded event) {
        if (emotionAnalysisRepository.findByEntryId(event.entryId()).isPresent()) {
            return;
        }

        EmotionAnalysisResult result = emotionAnalyzer.analyze(event.content());

        EmotionAnalysis analysis = EmotionAnalysis.create(
                event.entryId(), event.memberId(),
                result.emotionDetected(), result.keywords(), result.insightSummary(), result.flowHint(),
                result.emotionSummary(), result.emotionScore(), result.entryClarityScore(),
                result.entryClarityReason(), result.warmMessages());
        emotionAnalysisRepository.save(analysis);
    }

    @Transactional(readOnly = true)
    public EmotionAnalysisInfo getByEntryId(Long entryId, Long requesterId) {
        EmotionAnalysis analysis = emotionAnalysisRepository.findByEntryId(entryId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "아직 분석 결과가 없습니다."));

        if (!analysis.isOwnedBy(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "해당 분석 결과에 대한 접근 권한이 없습니다.");
        }

        return EmotionAnalysisInfo.from(analysis);
    }
}
