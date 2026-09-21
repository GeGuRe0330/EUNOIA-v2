package com.eunoia.completion.client;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringAiStructuredPromptClient implements StructuredPromptClient {

    private final ChatClient chatClient;

    @Override
    public <T> T call(String prompt, Class<T> responseType) {
        return chatClient.prompt(prompt)
                .call()
                .entity(responseType);
    }
}
