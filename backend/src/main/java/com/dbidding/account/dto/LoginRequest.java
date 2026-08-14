package com.dbidding.account.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
	@NotBlank @Pattern(regexp = SignupRequest.EMAIL_PATTERN) @Size(max = 255)
	String email,

	@NotBlank @Size(max = 128)
	String password
) {
}
