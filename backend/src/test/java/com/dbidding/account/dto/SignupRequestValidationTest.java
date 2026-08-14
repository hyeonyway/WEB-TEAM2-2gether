package com.dbidding.account.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class SignupRequestValidationTest {

	private static ValidatorFactory validatorFactory;
	private static Validator validator;

	@BeforeAll
	static void setUpValidator() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@AfterAll
	static void closeValidatorFactory() {
		validatorFactory.close();
	}

	@Test
	void 올바른_회원가입_요청은_검증을_통과한다() {
		SignupRequest request = new SignupRequest(
			"collector@example.com",
			"Password123!",
			"collector"
		);

		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	void 이메일_비밀번호_닉네임_형식이_잘못되면_검증에_실패한다() {
		SignupRequest request = new SignupRequest("invalid-email", "short", "김");

		Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

		assertThat(violations)
			.extracting(violation -> violation.getPropertyPath().toString())
			.contains("email", "password", "nickname");
	}

	@Test
	void 프로덕션에서_통과했던_잘못된_이메일과_단일문자비밀번호_특수문자닉네임을_거부한다() {
		SignupRequest request = new SignupRequest("1@1.2", "aaaaaaaa", "닉 네임!");

		assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
			.contains("email", "password", "nickname");
	}

	@Test
	void 비밀번호는_두종조합_10자와_세종조합_8자를_허용한다() {
		assertThat(validator.validate(new SignupRequest("collector@example.com", "abcde12345", "수집가1"))).isEmpty();
		assertThat(validator.validate(new SignupRequest("collector@example.com", "Abc123!x", "수집가1"))).isEmpty();
	}
}
