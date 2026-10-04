package com.eunoia.identity.application.dto;

public record ChangePasswordCommand(String currentPassword, String newPassword) {
}
