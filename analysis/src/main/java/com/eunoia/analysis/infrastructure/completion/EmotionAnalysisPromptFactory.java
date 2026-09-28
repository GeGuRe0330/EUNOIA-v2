package com.eunoia.analysis.infrastructure.completion;

import org.springframework.stereotype.Component;

@Component
public class EmotionAnalysisPromptFactory {

    public String entryAnalysisPrompt(String content) {
        return """
                ※ 이 분석은 감정의 정답을 찾기 위한 것이 아니에요.
                  감정을 부드럽게 객관화하고, 사용자가 스스로를 더 깊이 이해할 수 있도록 돕는 것이
                  목적이에요.

                아래 글은 한 사용자가 자신의 감정을 솔직하게 적어낸 감정일기예요. 이 글을 조심스럽게
                읽고, 다음 항목들을 채워주세요.

                - emotionDetected: 이 글에서 가장 뚜렷하게 느껴지는 대표 감정 하나(예: 불안, 기대, 슬픔).
                - keywords: 글에서 반복되거나 중심이 되는 감정 키워드 3~5개를 쉼표로 구분해서.
                - insightSummary: 감정이 어떤 이유에서 비롯되었는지를 따뜻하게 요약(2~3문장).
                - flowHint: 감정의 흐름을 감정 단어 중심으로, 유발 원인이나 맥락을 덧붙여 표현
                  (예: '성과에 대한 압박에서 오는 불안 → 자책으로 이어지는 무기력감').
                - emotionSummary: 글 전체에서 느껴지는 감정의 분위기를 감성적으로 해석한 짧은 에세이(한 문단).
                - emotionScore: 감정적 안정 상태를 0~100 사이 숫자로. 0에 가까울수록 불안정, 100에
                  가까울수록 안정.
                - entryClarityScore: 이 글이 이후 기간별 흐름 분석에 쓰일 만큼 맥락과 감정 흐름이
                  충분히 담겨 있는지 0~100 점수로.
                - entryClarityReason: 위 점수를 그렇게 준 이유를 한 문장으로("어떤 정보가 선명했는지/
                  부족했는지" 중심으로).
                - warmMessages: 사용자 곁에 잠시 함께 머무는 조용한 동행자로서 짧은 문장 3개.
                  1) 글에서 실제로 드러난 상태를 한 가지 짚어 존중하며 인정하는 문장(평가하지 않기).
                  2) 함께 느껴지는 둘 이상의 감정을 조용히 짚는 문장("~이 함께 느껴져요" 형태).
                  3) 해결이나 조언 없이, 감정을 해석하지 않은 채 조금 떨어진 거리에서 바라보며
                     마무리하는 문장.

                감정글:
                "%s"
                """.formatted(content);
    }
}
