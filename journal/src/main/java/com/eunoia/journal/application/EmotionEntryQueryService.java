package com.eunoia.journal.application;

import com.eunoia.journal.domain.EmotionEntry;
import com.eunoia.journal.domain.EmotionEntryRepository;
import com.eunoia.journal.query.DailyEntryCount;
import com.eunoia.journal.query.EmotionEntryContent;
import com.eunoia.journal.query.EmotionEntryListItem;
import com.eunoia.journal.query.EmotionEntryQueryApi;
import com.eunoia.journal.query.EmotionEntrySlice;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmotionEntryQueryService implements EmotionEntryQueryApi {

    private final EmotionEntryRepository emotionEntryRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findContent(Long memberId, Long entryId) {
        validateMemberId(memberId);
        if (entryId == null) {
            throw new IllegalArgumentException("entryId는 필수입니다.");
        }
        return emotionEntryRepository.findById(entryId)
                .filter(entry -> entry.isOwnedBy(memberId))
                .map(EmotionEntry::getContent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmotionEntryContent> findContentsByEntryIds(Long memberId, List<Long> entryIds) {
        validateMemberId(memberId);
        validateEntryIds(entryIds);

        return emotionEntryRepository.findByIdInAndMemberId(entryIds, memberId).stream()
                .map(entry -> new EmotionEntryContent(entry.getId(), entry.getContent()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsEntry(Long memberId, Long entryId) {
        validateMemberId(memberId);
        if (entryId == null) {
            throw new IllegalArgumentException("entryId는 필수입니다.");
        }

        return emotionEntryRepository.existsByIdAndMemberId(entryId, memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public EmotionEntrySlice findEntries(Long memberId, LocalDate from, LocalDate to, int page, int size) {
        validateMemberId(memberId);

        Slice<EmotionEntry> slice = emotionEntryRepository.findSliceByMemberIdAndPeriod(
                memberId, from, to, PageRequest.of(page, size));
        List<EmotionEntryListItem> items = slice.getContent().stream()
                .map(entry -> new EmotionEntryListItem(entry.getId(), entry.getEntryDate(), entry.getContent()))
                .toList();
        return new EmotionEntrySlice(items, slice.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public long countEntries(Long memberId, LocalDate from, LocalDate to) {
        validateMemberId(memberId);
        return emotionEntryRepository.countByMemberIdAndPeriod(memberId, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyEntryCount> countEntriesByDate(Long memberId, LocalDate from, LocalDate to) {
        validateMemberId(memberId);
        if (from == null || to == null) {
            throw new IllegalArgumentException("집계 기간은 필수입니다.");
        }
        return emotionEntryRepository.countDailyByMemberIdAndPeriod(memberId, from, to).stream()
                .map(count -> new DailyEntryCount(count.entryDate(), count.count()))
                .toList();
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId는 필수입니다.");
        }
    }

    private void validateEntryIds(List<Long> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            throw new IllegalArgumentException("entryIds는 필수입니다.");
        }
    }
}
