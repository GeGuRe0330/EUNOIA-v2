package com.eunoia.identity.application.dto;

import com.eunoia.identity.domain.Gender;

public record UpdateProfileCommand(String nickname, Integer age, Gender gender) {
}
