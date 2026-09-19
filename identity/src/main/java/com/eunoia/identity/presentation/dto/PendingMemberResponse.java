package com.eunoia.identity.presentation.dto;

import com.eunoia.identity.application.dto.PendingMemberInfo;
import com.eunoia.identity.domain.Gender;
import com.eunoia.identity.domain.Role;
import com.eunoia.identity.domain.Status;

import java.time.LocalDateTime;

public record PendingMemberResponse(
        Long id,
        String email,
        String nickname,
        Integer age,
        Gender gender,
        LocalDateTime createdAt,
        Status status,
        Role role
) {
    public static PendingMemberResponse from(PendingMemberInfo info) {
        return new PendingMemberResponse(
                info.id(),
                info.email(),
                info.nickname(),
                info.age(),
                info.gender(),
                info.createdAt(),
                info.status(),
                info.role()
        );
    }
}
