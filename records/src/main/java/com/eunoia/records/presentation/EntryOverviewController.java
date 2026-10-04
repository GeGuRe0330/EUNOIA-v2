package com.eunoia.records.presentation;

import com.eunoia.common.security.AuthenticatedPrincipal;
import com.eunoia.records.application.EntryOverviewService;
import com.eunoia.records.presentation.dto.CalendarResponse;
import com.eunoia.records.presentation.dto.EntryPageResponse;
import com.eunoia.records.presentation.dto.EntrySummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/emotion-entries")
@Tag(name = "기록 돌아보기", description = "마이페이지·열람 화면용 조회 API(journal·analysis 조합)")
public class EntryOverviewController {

    private final EntryOverviewService entryOverviewService;

    @GetMapping
    @Operation(summary = "감정일기 목록 조회", description = "조회 기간(from·to, 각각 생략 가능)·페이지로 본인 감정일기를 최신순으로 조회한다. 항목마다 SUCCESS 분석의 대표 감정을 함께 준다.")
    public EntryPageResponse getEntries(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return EntryPageResponse.from(entryOverviewService.getEntries(principal.getMemberId(), from, to, page, size));
    }

    @GetMapping("/summary")
    @Operation(summary = "기록 요약 조회", description = "본인 감정일기의 전체 글 수와 이번 달(서버기준) 글 수를 조회한다.")
    public EntrySummaryResponse getSummary(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return EntrySummaryResponse.from(entryOverviewService.getSummary(principal.getMemberId()));
    }

    @GetMapping("/calendar")
    @Operation(summary = "감정 캘린더 조회", description = "한 달 중 글이 있는 날만, 날짜별 글 수와 SUCCESS 분석의 평균 점수를 조회한다.")
    public CalendarResponse getCalendar(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth
    ) {
        return CalendarResponse.from(entryOverviewService.getCalendar(principal.getMemberId(), yearMonth));
    }
}
