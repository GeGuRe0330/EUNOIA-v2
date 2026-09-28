package com.eunoia.journal.application;

import com.eunoia.common.exception.BusinessException;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    @DisplayName("존재하지 않는 감정글을 조회하면 예외가 발생한다.")
    void getById_withNonExistentEntry_throws() {
        when(emotionEntryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emotionEntryService.getById(1L, 1L))
                .isExactlyInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("소유자가 아닌 회원이 조회하면 예외가 발생한다.")
    void getById_withNonOwner_throws() {
        EmotionEntry entry = EmotionEntry.write(1L, "오늘 하루", LocalDate.now());
        when(emotionEntryRepository.findById(1L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> emotionEntryService.getById(1L, 2L))
                .isExactlyInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("소유자가 조회하면 정상적으로 반환한다.")
    void getById_withOwner_returnEntry() {
        EmotionEntry entry = EmotionEntry.write(1L, "오늘 하루", LocalDate.now());
        when(emotionEntryRepository.findById(1L)).thenReturn(Optional.of(entry));

        EmotionEntryInfo result = emotionEntryService.getById(1L, 1L);

        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.content()).isEqualTo("오늘 하루");
    }

    @Test
    @DisplayName("목록을 조회하면 매핑된 목록을 반환한다.")
    void getMyEntries_returnMappedList() {
        EmotionEntry entry1 = EmotionEntry.write(1L, "첫째 날", LocalDate.of(2026, 9, 19));
        EmotionEntry entry2 = EmotionEntry.write(1L, "둘째 날", LocalDate.of(2026, 9 ,20));
        when(emotionEntryRepository.findByMemberIdOrderByEntryDateDesc(1L)).thenReturn(List.of(entry2, entry1));

        List<EmotionEntryInfo> result = emotionEntryService.getMyEntries(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).content()).isEqualTo("둘째 날");
        assertThat(result.get(1).content()).isEqualTo("첫째 날");
    }
}
