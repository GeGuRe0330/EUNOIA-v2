package com.eunoia.journal.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmotionEntryTest {

    @Test
    @DisplayName("작성자 ID가 null이면 작성할 수 없다.")
    void write_withNullMemberId_throws() {
        assertThatThrownBy(() -> EmotionEntry.write(null, "오늘 하루", LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("내용이 null이면 작성할 수 없다.")
    void write_withNullContent_throws() {
        assertThatThrownBy(() -> EmotionEntry.write(1L, null, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("내용이 비어있으면 작성할 수 없다.")
    void write_withBlankContent_throws() {
        assertThatThrownBy(() -> EmotionEntry.write(1L, "  ", LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("작성일을 지정하지 않으면 오늘 날짜로 채워진다.")
    void write_withNullEntryDate_setsToday() {
        EmotionEntry entry = EmotionEntry.write(1L, "오늘 하루", null);

        assertThat(entry.getEntryDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("유효한 값으로 작성하면 모든 필드가 그대로 저장된다.")
    void write_withValidArguments_setAllFields() {
        LocalDate entryDate = LocalDate.of(2026, 9, 20);

        EmotionEntry entry = EmotionEntry.write(1L, "오늘 하루", entryDate);

        assertThat(entry.getMemberId()).isEqualTo(1L);
        assertThat(entry.getContent()).isEqualTo("오늘 하루");
        assertThat(entry.getEntryDate()).isEqualTo(entryDate);
    }
}
