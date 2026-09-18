package com.eunoia.identity.presentation.dto;

import com.eunoia.identity.application.dto.RegisterMemberCommand;
import com.eunoia.identity.domain.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemberSignupRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank String nickname,
        @NotNull Integer age,
        @NotNull Gender gender
) {
    public RegisterMemberCommand toCommand() {
        return new RegisterMemberCommand(email, password, nickname, age, gender);
    }
}
