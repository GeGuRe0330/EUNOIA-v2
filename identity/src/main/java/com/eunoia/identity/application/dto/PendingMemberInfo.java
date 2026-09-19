package com.eunoia.identity.application.dto;

import com.eunoia.identity.domain.Gender;
import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.Role;
import com.eunoia.identity.domain.Status;

import java.time.LocalDateTime;

public record PendingMemberInfo(
        Long id,
        String email,
        String nickname,
        Integer age,
        Gender gender,
        LocalDateTime createdAt,
        Status status,
        Role role
) {
    public static PendingMemberInfo from(Member member) {
        return new PendingMemberInfo(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                member.getAge(),
                member.getGender(),
                member.getCreatedAt(),
                member.getStatus(),
                member.getRole()
        );
    }
}
