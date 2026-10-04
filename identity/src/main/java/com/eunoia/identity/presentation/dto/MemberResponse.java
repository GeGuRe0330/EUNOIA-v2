package com.eunoia.identity.presentation.dto;

import com.eunoia.identity.application.dto.MemberInfo;
import com.eunoia.identity.domain.Gender;
import com.eunoia.identity.domain.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record MemberResponse(
        Long id,
        String email,
        String nickname,
        Integer age,
        Gender gender,
        Role role,
        LocalDateTime createdAt,
        UUID profileImageId
) {
    public static MemberResponse from(MemberInfo info) {
        return new MemberResponse(
                info.id(),
                info.email(),
                info.nickname(),
                info.age(),
                info.gender(),
                info.role(),
                info.createdAt(),
                info.profileImageId());
    }
}
