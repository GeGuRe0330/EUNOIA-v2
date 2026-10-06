package com.eunoia.insight.application;

import com.eunoia.analysis.query.EmotionAnalysisCandidate;
import com.eunoia.analysis.query.EmotionAnalysisQueryApi;
import com.eunoia.insight.application.dto.MetaAnalysisHistoryItem;
import com.eunoia.insight.application.dto.MetaAnalysisInfo;
import com.eunoia.insight.domain.MetaAnalysisGenerationStatus;
import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisResultRepository;
import com.eunoia.insight.domain.MetaAnalysisStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MetaAnalysisService {

    private static final long PERIOD_LOOKBACK_DAYS = 29;

    private final EmotionAnalysisQueryApi analysisQueryApi;
    private final MetaAnalysisCandidateSelector candidateSelector;
    private final MetaAnalysisRegenerationGuard regenerationGuard;
    private final MetaAnalysisGenerationClaimer generationClaimer;
    private final MetaAnalysisResultRepository metaAnalysisResultRepository;

    @Transactional(readOnly = true)
    public MetaAnalysisInfo getLatest(Long memberId) {
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(PERIOD_LOOKBACK_DAYS);

        MetaAnalysisSelection selection = selectCandidates(memberId, periodStart, periodEnd);
        MetaAnalysisStatus status = resolveStatus(selection.selected().size());

        MetaAnalysisResult latestSuccess = metaAnalysisResultRepository.findLatestSuccessByMemberId(memberId).orElse(null);
        MetaAnalysisResult todayAttempt = metaAnalysisResultRepository.findLatestAttemptOfDay(memberId, periodEnd).orElse(null);

        return MetaAnalysisInfo.of(status, periodStart, periodEnd, selection.selected().size(), latestSuccess, todayAttempt);
    }

    // 트랜잭션 없이 조율만 한다 — GPT 호출은 비동기 워커가, 새 시도 행 생성은 Claimer가 별도 트랜잭션으로 한다
    public MetaAnalysisInfo generate(Long memberId) {
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(PERIOD_LOOKBACK_DAYS);

        MetaAnalysisSelection selection = selectCandidates(memberId, periodStart, periodEnd);
        List<EmotionAnalysisCandidate> selected = selection.selected();

        if (selected.size() < MetaAnalysisCandidateSelector.MAX_CANDIDATES) {
            return MetaAnalysisInfo.of(MetaAnalysisStatus.PREPARING, periodStart, periodEnd, selected.size(), null, null);
        }

        List<Long> entryIds = selected.stream().map(EmotionAnalysisCandidate::entryId).toList();
        Optional<MetaAnalysisResult> latestSuccess = metaAnalysisResultRepository.findLatestSuccessByMemberId(memberId);
        Optional<MetaAnalysisResult> todayAttempt = metaAnalysisResultRepository.findLatestAttemptOfDay(memberId, periodEnd);

        if (regenerationGuard.isUnchanged(entryIds, selection.excludedEntryCount(), latestSuccess)) {
            return MetaAnalysisInfo.of(MetaAnalysisStatus.READY, periodStart, periodEnd, selected.size(),
                    latestSuccess.orElse(null), todayAttempt.orElse(null));
        }

        if (todayAttempt.isPresent() && todayAttempt.get().getGenerationStatus() == MetaAnalysisGenerationStatus.PROCESSING) {
            return MetaAnalysisInfo.of(MetaAnalysisStatus.READY, periodStart, periodEnd, selected.size(),
                    latestSuccess.orElse(null), todayAttempt.get());
        }

        int nextAttemptNo = todayAttempt.map(attempt -> attempt.getAttemptNo() + 1).orElse(1);
        try {
            MetaAnalysisResult started = generationClaimer.claim(memberId, periodStart, periodEnd, nextAttemptNo,
                    selected.size(), selection.excludedEntryCount(), entryIds);
            return MetaAnalysisInfo.of(MetaAnalysisStatus.READY, periodStart, periodEnd, selected.size(),
                    latestSuccess.orElse(null), started);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청이 먼저 같은 시도 번호를 만들었다 — 그 진행 중인 작업의 상태를 돌려준다(GPT는 한 번만 호출됨)
            MetaAnalysisResult concurrent = metaAnalysisResultRepository.findLatestAttemptOfDay(memberId, periodEnd)
                    .filter(attempt -> attempt.getGenerationStatus() == MetaAnalysisGenerationStatus.PROCESSING)
                    .orElseThrow(() -> e);
            return MetaAnalysisInfo.of(MetaAnalysisStatus.READY, periodStart, periodEnd, selected.size(),
                    latestSuccess.orElse(null), concurrent);
        }
    }

    private MetaAnalysisSelection selectCandidates(Long memberId, LocalDate periodStart, LocalDate periodEnd) {
        List<EmotionAnalysisCandidate> candidates = analysisQueryApi.findSuccessfulAnalyses(memberId, periodStart, periodEnd);
        return candidateSelector.select(candidates);
    }

    private MetaAnalysisStatus resolveStatus(int selectedCount) {
        return selectedCount >= MetaAnalysisCandidateSelector.MAX_CANDIDATES
                ? MetaAnalysisStatus.READY
                : MetaAnalysisStatus.PREPARING;
    }

    @Transactional(readOnly = true)
    public List<MetaAnalysisHistoryItem> getHistory(Long memberId) {
        return metaAnalysisResultRepository.findAllSuccessByMemberId(memberId).stream()
                .map(MetaAnalysisHistoryItem::from)
                .toList();
    }
}
