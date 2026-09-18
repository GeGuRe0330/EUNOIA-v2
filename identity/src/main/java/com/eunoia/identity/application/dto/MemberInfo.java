package com.eunoia.identity.application.dto;

import com.eunoia.identity.domain.Gender;
import com.eunoia.identity.domain.Member;

public record MemberInfo(
        Long id,
        String email,
        String nickname,
        Integer age,
        Gender gender
) {
    public static MemberInfo from(Member member) {
        return new MemberInfo(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getAge(),
                member.getGender()
        );
    }
}
