package com.eunoia.analysis.presentation;

import com.eunoia.analysis.application.EmotionAnalysisService;
import com.eunoia.analysis.presentation.dto.EmotionAnalysisResponse;
import com.eunoia.analysis.presentation.dto.EmotionScorePointResponse;
import com.eunoia.common.security.AuthenticatedPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analyses")
@Tag(name = "감정분석", description = "감정일기 분석 결과 조회 API")
public class EmotionAnalysisController {

    private final EmotionAnalysisService emotionAnalysisService;

    @GetMapping("/by-entry/{entryId}")
    @Operation(summary = "감정분석 결과 조회", description = "본인이 작성한 일기의 감정분석 결과를 조회한다.")
    public EmotionAnalysisResponse getByEntryId(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long entryId
            ) {
        return EmotionAnalysisResponse.from(emotionAnalysisService.getByEntryId(entryId, principal.getMemberId()));
    }

    @GetMapping("/latest")
    @Operation(summary = "최신 분석 결과 조회", description = "본인의 가장 최근 감정분석 결과를 조회한다. 분석 결과가 하나도 없으면 data: null을 반환한다.")
    public EmotionAnalysisResponse getLatest(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return emotionAnalysisService.getLatest(principal.getMemberId())
                .map(EmotionAnalysisResponse::from)
                .orElse(null);
    }

    @GetMapping("/scores")
    @Operation(summary = "감정 점수 목록 조회", description = "본인의 최근 7건 감정 점수를 entryDate 오름차순으로 조회한다(차트용).")
    public List<EmotionScorePointResponse> getScores(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return emotionAnalysisService.getScores(principal.getMemberId()).stream()
                .map(EmotionScorePointResponse::from)
                .toList();
    }
}
