package com.eunoia.insight.domain;

import java.time.LocalDate;
import java.util.List;

// GPT 응답을 그대로 이 타입으로 역직렬화하므로, 목록 필드가 null·생략으로 와도 "근거 없음 = 빈 목록"으로 받아들인다
// (프롬프트가 "근거 없으면 비워도 됨"이라 GPT가 빈 배열 대신 null을 주는 경우가 있고, 복사 메서드는 null을 받으면 NPE가 난다).
// 도메인 / 영속 ( JSON 컬럼 ) / API 응답 세 계약을 동일 타입으로 공유하는 의도적 결합.
// TODO : API 안정화 이후 분리 필요성이 생기면 presentation 전용 DTO로 독립시킨다.
public record MetaAnalysisContent(
        Outer outer,
        Inner inner,
        Clarity clarity,
        List<RepresentativeEntry> evidence
) {
    public MetaAnalysisContent {
        evidence = copyOrEmpty(evidence);
    }

    // null은 "비어 있음"으로 취급한다
    private static <T> List<T> copyOrEmpty(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
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
            keywords = copyOrEmpty(keywords);
            triggers = copyOrEmpty(triggers);
            emotionFlows = copyOrEmpty(emotionFlows);
            copingPatterns = copyOrEmpty(copingPatterns);
            strengthSignals = copyOrEmpty(strengthSignals);
            sensitivePoints = copyOrEmpty(sensitivePoints);
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
            keywords = copyOrEmpty(keywords);
            coreValues = copyOrEmpty(coreValues);
            needs = copyOrEmpty(needs);
            innerMotivations = copyOrEmpty(innerMotivations);
        }
    }

    public record Clarity(
            Integer clarityScore,
            List<String> clarityReasons,
            List<String> notVisibleYet,
            List<String> nextActions
    ) {
        public Clarity {
            clarityReasons = copyOrEmpty(clarityReasons);
            notVisibleYet = copyOrEmpty(notVisibleYet);
            nextActions = copyOrEmpty(nextActions);
        }
    }

    public record RepresentativeEntry(
            Long entryId,
            LocalDate entryDate,
            String whySelected
    ) {}
}
