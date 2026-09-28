package com.eunoia.analysis.infrastructure.completion;

import com.eunoia.analysis.domain.EmotionAnalysisResult;
import com.eunoia.analysis.domain.EmotionAnalyzer;
import com.eunoia.completion.client.StructuredPromptClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompletionEmotionAnalyzer implements EmotionAnalyzer {

    private final StructuredPromptClient structuredPromptClient;
    private final EmotionAnalysisPromptFactory promptFactory;

    @Override
    public EmotionAnalysisResult analyze(String content) {
        String prompt = promptFactory.entryAnalysisPrompt(content);
        return structuredPromptClient.call(prompt, EmotionAnalysisResult.class);
    }
}
