package com.eunoia.journal.application;

import com.eunoia.journal.application.dto.EmotionEntryInfo;
import com.eunoia.journal.application.dto.WriteEmotionEntryCommand;
import com.eunoia.journal.domain.EmotionEntry;
import com.eunoia.journal.domain.EmotionEntryRepository;
import com.eunoia.journal.event.EmotionEntryRecorded;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmotionEntryServiceTest {

    @Mock
    private EmotionEntryRepository emotionEntryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private EmotionEntryService emotionEntryService;

    @BeforeEach
    void setUp() {
        emotionEntryService = new EmotionEntryService(emotionEntryRepository, eventPublisher);
    }

    @Test
    @DisplayName("작성하면 매핑된 필드로 저장한다.")
    void write_savesEntryWithMappedFields() {
        LocalDate entryDate = LocalDate.of(2026, 9, 20);
        ArgumentCaptor<EmotionEntry> captor = ArgumentCaptor.forClass(EmotionEntry.class);
        when(emotionEntryRepository.save(captor.capture())).thenAnswer(invocation -> captor.getValue());

        EmotionEntryInfo result = emotionEntryService.write(
                new WriteEmotionEntryCommand(1L, "오늘 하루", entryDate));

        assertThat(captor.getValue().getMemberId()).isEqualTo(1L);
        assertThat(captor.getValue().getContent()).isEqualTo("오늘 하루");
        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.content()).isEqualTo("오늘 하루");
        assertThat(result.entryDate()).isEqualTo(entryDate);
    }

    @Test
    @DisplayName("작성하면 EmotionEntryRecorded 이벤트를 발행한다.")
    void write_publishesEmotionEntryRecordedEvent() {
        LocalDate entryDate = LocalDate.of(2026, 9, 20);
        when(emotionEntryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<EmotionEntryRecorded> eventCaptor = ArgumentCaptor.forClass(EmotionEntryRecorded.class);

        emotionEntryService.write(new WriteEmotionEntryCommand(1L, "오늘 하루", entryDate));

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().memberId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().content()).isEqualTo("오늘 하루");
        assertThat(eventCaptor.getValue().entryDate()).isEqualTo(entryDate);
    }
}
