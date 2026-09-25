package com.eunoia.insight.domain;

import java.util.List;

// 도메인 / 영속 ( JSON 컬럼 ) / API 응답 세 계약을 동일 타입으로 공유하는 의도적 결합.
// TODO : API 안정화 이후 분리 필요성이 생기면 presentation 전용 DTO로 독립시킨다.
public record MetaAnalysisContent(
        Outer outer,
        Inner inner,
        Clarity clarity,
        List<RepresentativeEntry> evidence
) {
    public MetaAnalysisContent {
        evidence = List.copyOf(evidence);
    }

    public record Outer(
            String summary,
            List<String> keywords,
            List<String> triggers,
            List<String> emotionFlows,
            List<String> copingPatterns,
            List<String> strengthSignals,
            List<String> sensitivePoints
    ) {
        public Outer {
            keywords = List.copyOf(keywords);
            triggers = List.copyOf(triggers);
            emotionFlows = List.copyOf(emotionFlows);
            copingPatterns = List.copyOf(copingPatterns);
            strengthSignals = List.copyOf(strengthSignals);
            sensitivePoints = List.copyOf(sensitivePoints);
        }
    }

    public record Inner(
            String summary,
            List<String> keywords,
            List<String> coreValues,
            List<String> needs,
            List<String> innerMotivations,
            String outerInnerGap,
            String gapExplanation
    ) {
        public Inner {
            keywords = List.copyOf(keywords);
            coreValues = List.copyOf(coreValues);
            needs = List.copyOf(needs);
            innerMotivations = List.copyOf(innerMotivations);
        }
    }

    public record Clarity(
            Integer clarityScore,
            List<String> clarityReasons,
            List<String> notVisibleYet,
            List<String> nextActions
    ) {
        public Clarity {
            clarityReasons = List.copyOf(clarityReasons);
            notVisibleYet = List.copyOf(notVisibleYet);
            nextActions = List.copyOf(nextActions);
        }
    }

    public record RepresentativeEntry(
            Long entryId,
            String whySelected
    ) {}
}
