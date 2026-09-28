package com.eunoia.identity.domain;

import com.eunoia.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    private Member(String email, String encodedPassword, String nickname, Integer age, Gender gender) {
        validateEmail(email);
        validatePassword(encodedPassword);
        validateNickname(nickname);
        validateAge(age);
        validateGender(gender);

        this.email = email;
        this.password = encodedPassword;
        this.nickname = nickname;
        this.age = age;
        this.gender = gender;
        this.role = Role.USER;
        this.status = Status.PENDING;
    }

    public static Member register(String email, String encodedPassword, String nickname, Integer age, Gender gender) {
        return new Member(email, encodedPassword, nickname, age, gender);
    }

    public void approve() {
        if (this.status == Status.ACTIVE) {
            throw new IllegalStateException("이미 승인된 회원입니다.");
        }
        this.status = Status.ACTIVE;
    }

    private void validateGender(Gender gender) {
        if (gender == null) {
            throw new IllegalArgumentException("성별은 필수입니다.");
        }
    }

    private void validateAge(Integer age) {
        if (age == null ) {
            throw new IllegalArgumentException("나이는 필수입니다.");
        }

        if (age < 0) {
            throw new IllegalArgumentException("잘못된 나이값입니다.");
        }
    }

    private void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("닉네임은 필수입니다.");
        }
    }

    private void validatePassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
    }
}
