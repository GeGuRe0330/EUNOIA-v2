package com.eunoia.identity.application;

import com.eunoia.common.exception.BusinessException;
import com.eunoia.identity.application.dto.MemberInfo;
import com.eunoia.identity.application.dto.RegisterMemberCommand;
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
}
