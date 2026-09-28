package com.eunoia.analysis.domain;

public interface EmotionAnalyzer {
    EmotionAnalysisResult analyze(String content);
}
