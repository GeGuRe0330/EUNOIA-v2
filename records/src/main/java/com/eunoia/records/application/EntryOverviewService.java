package com.eunoia.records.application;

import com.eunoia.analysis.query.DailyAverageScore;
import com.eunoia.analysis.query.EmotionAnalysisQueryApi;
import com.eunoia.analysis.query.EntryEmotion;
import com.eunoia.common.exception.BusinessException;
import com.eunoia.journal.query.EmotionEntryListItem;
import com.eunoia.journal.query.EmotionEntryQueryApi;
import com.eunoia.journal.query.EmotionEntrySlice;
import com.eunoia.records.application.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 일부러 @Transactional을 걸지 않는다 — journal·analysis Query API가 각자 자기 트랜잭션으로 읽고,
// 두 조회 사이의 순간적인 차이(그 사이 분석이 막 완료됨 등)는 허용한다(모듈 간 eventual consistency, 00.OVERVIEW.md §5.9).
@Service
@RequiredArgsConstructor
public class EntryOverviewService {

    private static final int MAX_PAGE_SIZE = 50;

    private final EmotionEntryQueryApi emotionEntryQueryApi;
    private final EmotionAnalysisQueryApi emotionAnalysisQueryApi;

    public EntryPageInfo getEntries(Long memberId, LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "조회 기간의 시작 날짜가 종료 날짜보다 늦어요.");
        }
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "페이지 정보가 올바르지 않아요.");
        }
        EmotionEntrySlice slice = emotionEntryQueryApi.findEntries(memberId, from, to, page, size);
        List<Long> entryIds = slice.items().stream().map(EmotionEntryListItem::entryId).toList();
        Map<Long, String> emotions = emotionAnalysisQueryApi.findEmotionsByEntryIds(memberId, entryIds).stream()
                .collect(Collectors.toMap(EntryEmotion::entryId, EntryEmotion::emotionDetected));

        List<EntryListItemInfo> items = slice.items().stream()
                .map(item -> new EntryListItemInfo(item.entryId(), item.entryDate(), item.content(), emotions.get(item.entryId())))
                .toList();
        return new EntryPageInfo(items, page, size, slice.hasNext());
    }

    public EntrySummaryInfo getSummary(Long memberId) {
        YearMonth thisMonth = YearMonth.now();
        long total = emotionEntryQueryApi.countEntries(memberId, null, null);
        long month = emotionEntryQueryApi.countEntries(memberId, thisMonth.atDay(1), thisMonth.atEndOfMonth());

        return new EntrySummaryInfo(total, month);
    }

    public CalendarInfo getCalendar(Long memberId, YearMonth yearMonth) {
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();

        Map<LocalDate, Double> scores = emotionAnalysisQueryApi.findDailyAverageScores(memberId, from, to).stream()
                .collect(Collectors.toMap(DailyAverageScore::date, DailyAverageScore::averageScore));

        List<CalendarDayInfo> days = emotionEntryQueryApi.countEntriesByDate(memberId, from, to).stream()
                .map(count -> new CalendarDayInfo(count.date(), count.entryCount(), scores.get(count.date())))
                .toList();
        return new CalendarInfo(yearMonth, days);
    }
}
