package com.eunoia.journal.application;

import com.eunoia.journal.domain.EmotionEntryRepository;
import com.eunoia.journal.query.EmotionEntryContent;
import com.eunoia.journal.query.EmotionEntryQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmotionEntryQueryService implements EmotionEntryQueryApi {

    private final EmotionEntryRepository emotionEntryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EmotionEntryContent> findContentsByEntryIds(List<Long> entryIds) {
        validateEntryIds(entryIds);

        return emotionEntryRepository.findByIdIn(entryIds).stream()
                .map(entry -> new EmotionEntryContent(entry.getId(), entry.getContent()))
                .toList();
    }

    private void validateEntryIds(List<Long> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            throw new IllegalArgumentException("entryIds는 필수입니다.");
        }
    }
}
