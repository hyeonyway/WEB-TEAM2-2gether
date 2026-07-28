package com.dbidding.auth.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dbidding.auth.dto.SignupRequest;
import com.dbidding.auth.dto.SignupResponse;
import com.dbidding.auth.exception.DuplicateEmailException;
import com.dbidding.auth.exception.DuplicateNicknameException;
import com.dbidding.auth.service.AuthService;

@WebMvcTest(AuthController.class)
class AuthControllerSignupTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@Test
	void 회원가입하면_201과_공개된_사용자_정보만_반환한다() throws Exception {
		given(authService.signup(any(SignupRequest.class)))
			.willReturn(new SignupResponse(
				1,
				"collector@example.com",
				"collector",
				"USER",
				"ACTIVE"
			));

		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "collector@example.com",
					  "password": "Password123!",
					  "nickname": "collector"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.email").value("collector@example.com"))
			.andExpect(jsonPath("$.nickname").value("collector"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.encryptedPassword").doesNotExist())
			.andExpect(jsonPath("$.salt").doesNotExist());
	}

	@Test
	void 유효하지_않은_요청이면_400을_반환한다() throws Exception {
		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "not-an-email",
					  "password": "short",
					  "nickname": ""
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 중복_이메일이면_409를_반환한다() throws Exception {
		given(authService.signup(any(SignupRequest.class)))
			.willThrow(new DuplicateEmailException());

		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest()))
			.andExpect(status().isConflict());
	}

	@Test
	void 중복_닉네임이면_409를_반환한다() throws Exception {
		given(authService.signup(any(SignupRequest.class)))
			.willThrow(new DuplicateNicknameException());

		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest()))
			.andExpect(status().isConflict());
	}

	@Test
	void UNIQUE가_아닌_무결성_위반은_409로_변환하지_않는다() {
		DataIntegrityViolationException databaseError =
			new DataIntegrityViolationException("not a duplicate");
		given(authService.signup(any(SignupRequest.class)))
			.willThrow(databaseError);

		assertThatThrownBy(() -> mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest())))
			.hasCause(databaseError);
	}

	private String validRequest() {
		return """
			{
			  "email": "collector@example.com",
			  "password": "Password123!",
			  "nickname": "collector"
			}
			""";
	}
}
