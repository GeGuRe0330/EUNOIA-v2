package com.eunoia.identity.application.dto;

import com.eunoia.identity.domain.Gender;

public record RegisterMemberCommand(
        String email,
        String password,
        String nickname,
        Integer age,
        Gender gender
) {
}
