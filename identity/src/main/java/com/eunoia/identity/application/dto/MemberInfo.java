package com.eunoia.identity.application.dto;

import com.eunoia.identity.domain.Gender;
import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record MemberInfo(
        Long id,
        String email,
        String nickname,
        Integer age,
        Gender gender,
        Role role,
        LocalDateTime createdAt,
        UUID profileImageId
) {
    public static MemberInfo from(Member member) {
        return new MemberInfo(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getAge(),
                member.getGender(),
                member.getRole(),
                member.getCreatedAt(),
                member.getProfileImageId()
        );
    }
}
