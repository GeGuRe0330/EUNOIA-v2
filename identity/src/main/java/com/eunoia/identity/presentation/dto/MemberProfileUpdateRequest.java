package com.eunoia.identity.presentation.dto;

import com.eunoia.identity.application.dto.UpdateProfileCommand;
import com.eunoia.identity.domain.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record MemberProfileUpdateRequest(
        @NotBlank String nickname,
        @NotNull @PositiveOrZero Integer age,
        @NotNull Gender gender
        ) {
    public UpdateProfileCommand toCommand() {
        return new UpdateProfileCommand(nickname, age, gender);
    }
}
