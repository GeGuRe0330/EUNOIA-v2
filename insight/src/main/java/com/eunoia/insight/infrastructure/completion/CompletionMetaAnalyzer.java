package com.eunoia.insight.infrastructure.completion;

import com.eunoia.completion.client.StructuredPromptClient;
import com.eunoia.insight.domain.MetaAnalysisAiResponse;
import com.eunoia.insight.domain.MetaAnalysisAnalyzer;
import com.eunoia.insight.domain.MetaAnalysisInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompletionMetaAnalyzer implements MetaAnalysisAnalyzer {

    private final StructuredPromptClient structuredPromptClient;
    private final MetaAnalysisPromptFactory promptFactory;

    @Override
    public MetaAnalysisAiResponse analyze(MetaAnalysisInput input) {
        String prompt = promptFactory.metaAnalysisPrompt(input);
        return structuredPromptClient.call(prompt, MetaAnalysisAiResponse.class);
    }
}
