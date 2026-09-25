package com.eunoia.insight.application;

import com.eunoia.analysis.query.EmotionAnalysisCandidate;
import com.eunoia.analysis.query.EmotionAnalysisQueryApi;
import com.eunoia.insight.application.dto.MetaAnalysisInfo;
import com.eunoia.insight.domain.MetaAnalysisAiResponse;
import com.eunoia.insight.domain.MetaAnalysisAnalyzer;
import com.eunoia.insight.domain.MetaAnalysisContent;
import com.eunoia.insight.domain.MetaAnalysisInput;
import com.eunoia.insight.domain.MetaAnalysisResult;
import com.eunoia.insight.domain.MetaAnalysisResultRepository;
import com.eunoia.insight.domain.MetaAnalysisStatus;
import com.eunoia.journal.query.EmotionEntryContent;
import com.eunoia.journal.query.EmotionEntryQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetaAnalysisService {

    private static final long PERIOD_LOOKBACK_DAYS = 29;

    private final EmotionAnalysisQueryApi analysisQueryApi;
    private final EmotionEntryQueryApi journalQueryApi;
    private final MetaAnalysisCandidateSelector candidateSelector;
    private final MetaAnalysisRegenerationGuard regenerationGuard;
    private final MetaAnalysisAnalyzer analyzer;
    private final MetaAnalysisResultRepository metaAnalysisResultRepository;

    @Transactional(readOnly = true)
    public MetaAnalysisInfo getLatest(Long memberId) {
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(PERIOD_LOOKBACK_DAYS);

        MetaAnalysisSelection selection = selectCandidates(memberId, periodStart, periodEnd);
        MetaAnalysisStatus status = resolveStatus(selection.selected().size());

        MetaAnalysisResult latest = metaAnalysisResultRepository.findLatestByMemberId(memberId).orElse(null);

        return MetaAnalysisInfo.of(status, periodStart, periodEnd, selection.selected().size(), latest);
    }

    @Transactional
    public MetaAnalysisInfo generate(Long memberId) {
        LocalDate periodEnd = LocalDate.now();
        LocalDate periodStart = periodEnd.minusDays(PERIOD_LOOKBACK_DAYS);

        MetaAnalysisSelection selection = selectCandidates(memberId, periodStart, periodEnd);
        List<EmotionAnalysisCandidate> selected = selection.selected();

        if (selected.size() < MetaAnalysisCandidateSelector.MAX_CANDIDATES) {
            return MetaAnalysisInfo.of(MetaAnalysisStatus.PREPARING, periodStart, periodEnd, selected.size(), null);
        }

        List<Long> entryIds = selected.stream().map(EmotionAnalysisCandidate::entryId).toList();
        Optional<MetaAnalysisResult> latest = metaAnalysisResultRepository.findLatestByMemberId(memberId);

        if (regenerationGuard.isUnchanged(entryIds, selection.excludedEntryCount(), latest)) {
            return MetaAnalysisInfo.of(MetaAnalysisStatus.READY, periodStart, periodEnd, selected.size(), latest.orElse(null));
        }

        MetaAnalysisContent content = buildContent(memberId, selected, entryIds, selection.excludedEntryCount());

        MetaAnalysisResult saved = upsert(memberId, periodStart, periodEnd, selected.size(), selection.excludedEntryCount(), content);

        return MetaAnalysisInfo.of(MetaAnalysisStatus.READY, periodStart, periodEnd, selected.size(), saved);
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

    private MetaAnalysisContent buildContent(Long memberId, List<EmotionAnalysisCandidate> selected, List<Long> entryIds,
                                              int excludedEntryCount) {
        Map<Long, String> contentsByEntryId = journalQueryApi.findContentsByEntryIds(memberId, entryIds).stream()
                .collect(Collectors.toMap(EmotionEntryContent::entryId, EmotionEntryContent::content));

        List<String> entryContents = selected.stream()
                .map(candidate -> contentsByEntryId.get(candidate.entryId()))
                .toList();

        int clarityScoreAverage = calculateClarityScoreAverage(selected);

        MetaAnalysisInput input = new MetaAnalysisInput(entryContents, excludedEntryCount, clarityScoreAverage);
        MetaAnalysisAiResponse aiResponse = analyzer.analyze(input);

        List<MetaAnalysisContent.RepresentativeEntry> evidence = selected.stream()
                .map(candidate -> new MetaAnalysisContent.RepresentativeEntry(
                        candidate.entryId(), resolveWhySelected(candidate.entryClarityReason())))
                .toList();

        MetaAnalysisContent.Clarity clarity = new MetaAnalysisContent.Clarity(
                clarityScoreAverage,
                aiResponse.clarity().clarityReasons(),
                aiResponse.clarity().notVisibleYet(),
                aiResponse.clarity().nextActions());

        return new MetaAnalysisContent(aiResponse.outer(), aiResponse.inner(), clarity, evidence);
    }

    private int calculateClarityScoreAverage(List<EmotionAnalysisCandidate> selected) {
        return (int) Math.round(selected.stream()
                .mapToInt(EmotionAnalysisCandidate::entryClarityScore)
                .average()
                .orElse(0));
    }

    private String resolveWhySelected(String entryClarityReason) {
        if (entryClarityReason == null || entryClarityReason.isBlank()) {
            return "감정의 흐름과 맥락이 비교적 선명하게 드러나 있어요.";
        }
        return entryClarityReason;
    }

    private MetaAnalysisResult upsert(Long memberId, LocalDate periodStart, LocalDate periodEnd,
                                       int basedOnCount, int excludedEntryCount, MetaAnalysisContent content) {
        return metaAnalysisResultRepository.findByMemberIdAndPeriodEnd(memberId, periodEnd)
                .map(existing -> {
                    existing.update(basedOnCount, excludedEntryCount, content);
                    return metaAnalysisResultRepository.save(existing);
                })
                .orElseGet(() -> metaAnalysisResultRepository.save(
                        MetaAnalysisResult.create(memberId, periodStart, periodEnd, basedOnCount, excludedEntryCount, content)));
    }
}
