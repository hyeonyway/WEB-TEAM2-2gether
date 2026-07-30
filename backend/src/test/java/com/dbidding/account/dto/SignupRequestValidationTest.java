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
}
