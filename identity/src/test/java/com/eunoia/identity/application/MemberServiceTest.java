package com.eunoia.identity.application;

import com.eunoia.common.exception.BusinessException;
import com.eunoia.identity.application.dto.ChangePasswordCommand;
import com.eunoia.identity.application.dto.MemberInfo;
import com.eunoia.identity.application.dto.RegisterMemberCommand;
import com.eunoia.identity.application.dto.UpdateProfileCommand;
import com.eunoia.identity.domain.Gender;
import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.MemberRepository;
import com.eunoia.identity.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MemberService memberService;


    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, passwordEncoder);
    }

    @Test
    @DisplayName("이미 가입된 이메일로 가입하면 예외가 발생한다.")
    void register_withDuplicateEmail_throws() {
        when(memberRepository.existsByEmail("test@test.com")).thenReturn(true);

        assertThatThrownBy(() -> memberService.register(
                new RegisterMemberCommand("test@test.com", "rawPassword1!", "하나", 20, Gender.FEMALE)))
                .isInstanceOf(BusinessException.class);

        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("가입 시 비밀번호를 인코딩해서 저장한다.")
    void register_encodesPasswordBeforeSaving() {
        when(memberRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(passwordEncoder.encode("rawPassword1!")).thenReturn("encoded-password");
        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        when(memberRepository.save(captor.capture())).thenAnswer(invocation -> captor.getValue());

        MemberInfo result = memberService.register(
                new RegisterMemberCommand("test@test.com", "rawPassword1!", "하나",  20, Gender.FEMALE));

        assertThat(captor.getValue().getPassword()).isEqualTo("encoded-password");
        assertThat(result.email()).isEqualTo("test@test.com");
        assertThat(result.nickname()).isEqualTo("하나");
        assertThat(result.age()).isEqualTo(20);
        assertThat(result.gender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    @DisplayName("존재하는 회원 ID로 조회하면 정보를 반환한다.")
    void getMe_withExistingMember_returnsInfo() {
        Member member = Member.register("test@test.com", "encoded-password", "하나", 20, Gender.FEMALE);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        MemberInfo result = memberService.getMe(1L);

        assertThat(result.email()).isEqualTo("test@test.com");
        assertThat(result.nickname()).isEqualTo("하나");
        assertThat(result.age()).isEqualTo(20);
        assertThat(result.gender()).isEqualTo(Gender.FEMALE);
        assertThat(result.role()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("존재하지 않는 회원 ID로 조회하면 예외가 발생한다.")
    void getMe_withNonExistentMember_throws() {
        when(memberRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMe(999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("프로필을 수정하면 DB에서 다시 조회한 회원의 닉네임·나이·성별이 바뀌고 갱신된 정보를 반환한다.")
    void updateProfile_withExistingMember_changesAndReturnsInfo() {
        Member member = Member.register("test@test.com", "encoded-password", "하나", 20, Gender.FEMALE);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        MemberInfo result = memberService.updateProfile(1L, new UpdateProfileCommand("둘", 30, Gender.NONE));

        assertThat(member.getNickname()).isEqualTo("둘");
        assertThat(result.nickname()).isEqualTo("둘");
        assertThat(result.age()).isEqualTo(30);
        assertThat(result.gender()).isEqualTo(Gender.NONE);
        verify(memberRepository, never()).save(any()); // 트랜잭션 안 변경 감지로 저장 — save 불필요
    }

    @Test
    @DisplayName("현재 비밀번호가 맞으면 새 비밀번호를 인코딩해서 교체한다.")
    void changePassword_withCorrectCurrentPassword_encodesAndReplaces() {
        Member member = Member.register("test@test.com", "old-encoded", "하나", 20, Gender.FEMALE);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("oldPassword", "old-encoded")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("new-encoded");

        memberService.changePassword(1L, new ChangePasswordCommand("oldPassword", "newPassword"));

        assertThat(member.getPassword()).isEqualTo("new-encoded");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 401이 아닌 400 BusinessException이고 비밀번호는 바뀌지 않는다.")
    void changePassword_withWrongCurrentPassword_throws400AndKeepsPassword() {
        Member member = Member.register("test@test.com", "old-encoded", "하나", 20, Gender.FEMALE);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrongPassword", "old-encoded")).thenReturn(false);

        assertThatThrownBy(() -> memberService.changePassword(1L, new ChangePasswordCommand("wrongPassword", "newPassword")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("지금 쓰는 비밀번호가 맞지 않아요.");
                });
        assertThat(member.getPassword()).isEqualTo("old-encoded");
        verify(passwordEncoder, never()).encode(any());
    }
}
