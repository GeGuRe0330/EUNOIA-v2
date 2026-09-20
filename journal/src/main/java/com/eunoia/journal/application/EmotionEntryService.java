package com.eunoia.journal.application;

import com.eunoia.journal.application.dto.EmotionEntryInfo;
import com.eunoia.journal.application.dto.WriteEmotionEntryCommand;
import com.eunoia.journal.domain.EmotionEntry;
import com.eunoia.journal.domain.EmotionEntryRepository;
import com.eunoia.journal.event.EmotionEntryRecorded;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmotionEntryService {

    private final EmotionEntryRepository emotionEntryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public EmotionEntryInfo write(WriteEmotionEntryCommand command) {
        EmotionEntry entry = emotionEntryRepository.save(
                EmotionEntry.write(command.memberId(), command.content(), command.entryDate()));

        eventPublisher.publishEvent(
                EmotionEntryRecorded.of(entry.getId(), entry.getMemberId(), entry.getContent(), entry.getEntryDate()));

        return EmotionEntryInfo.from(entry);
    }
}
