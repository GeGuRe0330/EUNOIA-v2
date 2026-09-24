package com.eunoia.insight.presentation;

import com.eunoia.common.security.AuthenticatedPrincipal;
import com.eunoia.insight.application.MetaAnalysisService;
import com.eunoia.insight.presentation.dto.MetaAnalysisResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/meta-analyses")
@Tag(name = "메타분석", description = "기간별 감정 흐름 메타 분석 API")
public class MetaAnalysisController {

    private final MetaAnalysisService metaAnalysisService;

    @GetMapping("/latest")
    @Operation(summary = "최신 메타분석 상태/결과 조회", description = "현재 준비 상태와 가장 최근 메타분석 결과를 조회한다.")
    public MetaAnalysisResponse getLatest(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return MetaAnalysisResponse.from(metaAnalysisService.getLatest(principal.getMemberId()));
    }

    @PostMapping
    @Operation(summary = "메타분석 생성", description = "조건이 충족되면 메타분석을 생성하거나 갱신한다.")
    public MetaAnalysisResponse generate(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return MetaAnalysisResponse.from(metaAnalysisService.generate(principal.getMemberId()));
    }
}
