package com.eunoia.journal.domain;

import com.eunoia.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "emotion_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmotionEntry extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private LocalDate entryDate;

    private EmotionEntry(Long memberId, String content, LocalDate entryDate) {
        validateMemberId(memberId);
        validateContent(content);

        this.memberId = memberId;
        this.content = content;
        this.entryDate = entryDate != null ? entryDate : LocalDate.now();
    }

    public static EmotionEntry write(Long memberId, String content, LocalDate entryDate) {
        return new EmotionEntry(memberId, content, entryDate);
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("감정글은 필수입니다.");
        }
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("작성자 ID는 필수입니다.");
        }
    }
}
