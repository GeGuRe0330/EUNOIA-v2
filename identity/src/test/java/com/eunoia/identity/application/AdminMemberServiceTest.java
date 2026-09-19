package com.eunoia.identity.application;

import com.eunoia.common.exception.BusinessException;
import com.eunoia.identity.application.dto.PendingMemberInfo;
import com.eunoia.identity.domain.Gender;
import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.MemberRepository;
import com.eunoia.identity.domain.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class AdminMemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    private AdminMemberService adminMemberService;

    @BeforeEach
    void setUp() {
        adminMemberService = new AdminMemberService(memberRepository);
    }

    @Test
    @DisplayName("승인 대기 회원 목록을 조회한다.")
    void getPendingMembers_returnMappedList() {
        Member member = Member.register("test@test.com", "encoded", "하나", 20, Gender.FEMALE);
        when(memberRepository.findByStatus(Status.PENDING)).thenReturn(List.of(member));

        List<PendingMemberInfo> result = adminMemberService.getPendingMembers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("test@test.com");
    }

    @Test
    @DisplayName("존재하지 않는 회원을 승인하면 예외가 발생한다.")
    void approve_withNonExistentMember_throws() {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminMemberService.approve(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("승인하면 회원 상태가 ACTIVE로 바뀐다.")
    void approve_setsStatusActive() {
        Member member = Member.register("test@test.com", "encoded", "하나", 20, Gender.FEMALE);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        adminMemberService.approve(1L);

        assertThat(member.getStatus()).isEqualTo(Status.ACTIVE);
    }

    @Test
    @DisplayName("이미 승인된 회원을 다시 승인하면 예외가 발생한다.")
    void approve_alreadyActive_throws() {
        Member member = Member.register("test@test.com", "encoded", "하나", 20, Gender.FEMALE);
        member.approve();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> adminMemberService.approve(1L))
            .isInstanceOf(IllegalStateException.class);
    }
}
