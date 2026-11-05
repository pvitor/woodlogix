package com.woodlogix.desktop.service;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CustomerData(
        @NotBlank String fullName,
        @Pattern(regexp = "^\\+?[0-9][0-9\\s().-]{5,19}$") String phone,
        @Email String email,
        String address
) {
}
