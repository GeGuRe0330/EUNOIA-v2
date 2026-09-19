package com.eunoia.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    @Test
    @DisplayName("이메일은 null이면 가입할 수 없다.")
    void register_withNullEmail_throws() {
        assertThatThrownBy(() -> Member.register(null, "encoded", "하나", 20, Gender.FEMALE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이메일이 비어있으면 가입할 수 없다.")
    void register_withBlankEmail_throws() {
        assertThatThrownBy(() -> Member.register("", "encoded", "하나", 20, Gender.FEMALE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호가 null이면 가입할 수 없다.")
    void register_withNullPassword_throws() {
        assertThatThrownBy(() -> Member.register("test@test.com", null, "하나",20, Gender.FEMALE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호가 비어있으면 가입할 수 없다.")
    void register_withBlankPassword_throws() {
        assertThatThrownBy(() -> Member.register("test@test.com", "  ", "하나", 20, Gender.FEMALE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("닉네임이 null이면 가입할 수 없다.")
    void register_withNullNickname_throws() {
        assertThatThrownBy(() -> Member.register("test@test.com", "encoded", null, 20, Gender.FEMALE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("닉네임이 비어있으면 가입할 수 없다.")
    void register_withBlankNickname_throws() {
        assertThatThrownBy(() -> Member.register("test@test.com", "encoded", "  ", 20, Gender.FEMALE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("나이가 null이면 가입할 수 없다.")
    void register_withNullAge_throws() {
        assertThatThrownBy(() -> Member.register("test@test.com", "encoded", "하나", null, Gender.FEMALE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("나이가 음수면 가입할 수 없다.")
    void register_withNegativeAge_throws() {
        assertThatThrownBy(() -> Member.register("test@test.com", "encoded", "하나", -1, Gender.FEMALE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("성별이 null이면 가입할 수 없다.")
    void register_withNullGender_throws() {
        assertThatThrownBy(() -> Member.register("test@test.com", "encoded", "하나", 20, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("승인하면 상태가 ACTIVE로 바뀐다.")
    void approve_fromPending_setsActive() {
        Member member = Member.register("test@test.com", "encoded", "하나", 20, Gender.FEMALE);

        member.approve();

        assertThat(member.getStatus()).isEqualTo(Status.ACTIVE);
    }

    @Test
    @DisplayName("이미 승인된 회원은 다시 승인하면 예외가 발생한다.")
    void approve_alreadyActive_throws() {
        Member member = Member.register("test@test.com", "encoded", "하나", 20, Gender.FEMALE);
        member.approve();

        assertThatThrownBy(member :: approve)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("유효한 값으로 가입하면 모든 필드가 그대로 저장된다.")
    void register_withValidArguments_setAllFields() {
        Member member = Member.register("test@test.com", "encoded", "하나", 20, Gender.FEMALE);

        assertThat(member.getEmail()).isEqualTo("test@test.com");
        assertThat(member.getPassword()).isEqualTo("encoded");
        assertThat(member.getNickname()).isEqualTo("하나");
        assertThat(member.getAge()).isEqualTo(20);
        assertThat(member.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(member.getRole()).isEqualTo(Role.USER);
        assertThat(member.getStatus()).isEqualTo(Status.PENDING);
    }
}
