package com.borrowbox.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
        @NotBlank(message = "Full name is required") String fullName,
        @NotBlank(message = "Email is required")
        @Email(message = "Email should be valid") String email,
        @NotBlank(message = "Password is required") String password
) {
}
