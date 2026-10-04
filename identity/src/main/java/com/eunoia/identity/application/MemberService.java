package com.eunoia.identity.application;

import com.eunoia.common.exception.BusinessException;
import com.eunoia.identity.application.dto.ChangePasswordCommand;
import com.eunoia.identity.application.dto.MemberInfo;
import com.eunoia.identity.application.dto.RegisterMemberCommand;
import com.eunoia.identity.application.dto.UpdateProfileCommand;
import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberInfo register(RegisterMemberCommand command) {
        if (memberRepository.existsByEmail(command.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 가입된 이메일이에요.");
        }

        String encodedPassword = passwordEncoder.encode(command.password());
        Member member = memberRepository.save(
                Member.register(command.email(), encodedPassword, command.nickname(), command.age(), command.gender()));
        return MemberInfo.from(member);
    }

    @Transactional(readOnly = true)
    public MemberInfo getMe(Long memberId) {
        return MemberInfo.from(getMember(memberId));
    }

    @Transactional
    public MemberInfo updateProfile(Long memberId, UpdateProfileCommand command) {
        Member member = getMember(memberId);
        member.updateProfile(command.nickname(),  command.age(), command.gender());
        return MemberInfo.from(member);
    }

    @Transactional
    public void changePassword(Long memberId, ChangePasswordCommand command) {
        Member member = getMember(memberId);
        if (!passwordEncoder.matches(command.currentPassword(), member.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "지금 쓰는 비밀번호가 맞지 않아요.");
        }
        member.changePassword(passwordEncoder.encode(command.newPassword()));
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원이에요."));
    }
}
