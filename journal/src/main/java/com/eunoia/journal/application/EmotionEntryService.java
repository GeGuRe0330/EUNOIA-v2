package com.eunoia.journal.application;

import com.eunoia.common.exception.BusinessException;
import com.eunoia.journal.application.dto.EmotionEntryInfo;
import com.eunoia.journal.application.dto.WriteEmotionEntryCommand;
import com.eunoia.journal.domain.EmotionEntry;
import com.eunoia.journal.domain.EmotionEntryRepository;
import com.eunoia.journal.event.EmotionEntryRecorded;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional(readOnly = true)
    public EmotionEntryInfo getById(Long entryId, Long requestId) {
        EmotionEntry entry = emotionEntryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 감정글입니다."));

        if (!entry.isOwnedBy(requestId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "해당 감정글에 대한 접근 권한이 없습니다.");
        }

        return EmotionEntryInfo.from(entry);
    }

    @Transactional(readOnly = true)
    public List<EmotionEntryInfo> getMyEntries(Long memberId) {
        return emotionEntryRepository.findByMemberIdOrderByEntryDateDesc(memberId).stream()
                .map(EmotionEntryInfo::from)
                .toList();
    }
}
