package com.eunoia.completion.client;

public interface StructuredPromptClient {
    <T> T call(String prompt, Class<T> responseType);
}
