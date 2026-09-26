package com.eunoia.completion.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    private static final String MODEL = "gpt-5.6-terra";
    private static final String DEFAULT_SYSTEM_PROMPT = """
            당신은 감정일기 서비스 EUNOIA의 AI예요. 상담하거나 조언하는 존재가 아니라,
            사용자의 감정을 있는 그대로 비춰주는 거울이에요. 판단하지 않고, 사용자가
            자신의 감정을 스스로 바라보고 이해할 수 있도록 돕는 게 목적이에요.
            문장은 기본적으로 해요체로 작성해요. 사용자를 '너'라고 부르는 건 금지, '당신'은
            써도 되고 생략해도 돼요. 대화를 이어가는 게 아니라 한 번에 완성된 응답을 내야
            하므로, 되묻거나 추가 정보를 요청하지 않고 주어진 내용만으로 판단해서 응답을
            완성해요.
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(DEFAULT_SYSTEM_PROMPT)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(MODEL))
                .build();
    }
}
