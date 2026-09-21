package com.eunoia.analysis.presentation;

import com.eunoia.analysis.application.EmotionAnalysisService;
import com.eunoia.analysis.presentation.dto.EmotionAnalysisResponse;
import com.eunoia.common.security.AuthenticatedPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analyses")
@Tag(name = "감정분석", description = "감정일기 분석 결과 조회 API")
public class EmotionAnalysisController {

    private final EmotionAnalysisService emotionAnalysisService;

    @GetMapping("/{entryId}")
    @Operation(summary = "감정분석 결과 조회", description = "본인이 작성한 일기의 감정분석 결과를 조회한다.")
    public EmotionAnalysisResponse getByEntryId(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long entryId
            ) {
        return EmotionAnalysisResponse.from(emotionAnalysisService.getByEntryId(entryId, principal.getMemberId()));
    }
}
