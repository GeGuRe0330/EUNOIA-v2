package com.eunoia.identity.presentation.dto;

import com.eunoia.identity.application.dto.ChangePasswordCommand;
import jakarta.validation.constraints.NotBlank;

public record MemberPasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) {
    public ChangePasswordCommand toCommand() {
        return new ChangePasswordCommand(currentPassword, newPassword);
    }
}
