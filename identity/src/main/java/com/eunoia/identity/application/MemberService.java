package com.eunoia.identity.application;

import com.eunoia.identity.application.dto.MemberInfo;
import com.eunoia.identity.application.dto.RegisterMemberCommand;
import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
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
            throw new IllegalStateException("이미 가입된 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(command.password());
        Member member = memberRepository.save(
                Member.register(command.email(), encodedPassword, command.nickname(), command.age(), command.gender()));
        return MemberInfo.from(member);
    }
}
