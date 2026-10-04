package com.eunoia.identity.presentation.dto;

import com.eunoia.identity.application.dto.MemberInfo;
import com.eunoia.identity.domain.Gender;
import com.eunoia.identity.domain.Role;

import java.time.LocalDateTime;

public record MemberResponse(
        Long id,
        String email,
        String nickname,
        Integer age,
        Gender gender,
        Role role,
        LocalDateTime createdAt
) {
    public static MemberResponse from(MemberInfo info) {
        return new MemberResponse(info.id(),  info.email(), info.nickname(), info.age(), info.gender(), info.role(), info.createdAt());
    }
}
