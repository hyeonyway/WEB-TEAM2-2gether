package com.dbidding.account.dto;

import com.dbidding.account.validation.PasswordPolicy;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
	@NotBlank @Pattern(regexp = EMAIL_PATTERN) @Size(max = 255)
	String email,

	@NotBlank @Size(min = 8, max = 128) @PasswordPolicy
	String password,

	@NotBlank @Size(min = 2, max = 30) @Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다.")
	String nickname
) {
	public static final String EMAIL_PATTERN = "^[A-Za-z0-9](?:[A-Za-z0-9._%+-]*[A-Za-z0-9])?@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$";
}
