package com.eunoia.journal.domain;

import com.eunoia.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@SQLRestriction("deleted_at IS NULL") // 소프트 삭제된 글은 findById, 파생쿼리, JPQL 전부에서 자동 제외 ( 네이티브 쿼리는 미적용 - 직접 조건 추가 )
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
    @Column(nullable = false, length = Integer.MAX_VALUE)
    private String content;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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

    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}
