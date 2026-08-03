package com.dbidding.account.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.authentication.AuthenticationStrategy;
import com.dbidding.account.authentication.CredentialAuthenticationService;
import com.dbidding.account.cookie.RefreshCookieFactory;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.dto.LoginRequest;
import com.dbidding.account.dto.LoginResponse;
import com.dbidding.account.exception.InvalidCredentialsException;
import com.dbidding.account.service.SignupService;

@WebMvcTest(AuthController.class)
class AuthControllerLoginTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SignupService signupService;

	@MockitoBean
	private CredentialAuthenticationService credentialAuthenticationService;

	@MockitoBean
	private AuthenticationStrategy authenticationStrategy;

	@MockitoBean
	private RefreshCookieFactory refreshCookieFactory;

	@Test
	void 로그인하면_access만_응답하고_refresh는_host_only_cookie로_전달한다() throws Exception {
		AuthenticatedAccount account = new AuthenticatedAccount(1, AccountRole.USER);
		ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "refresh-token")
			.httpOnly(true)
			.secure(true)
			.path("/api/auth")
			.maxAge(604800)
			.sameSite("Strict")
			.build();
		given(credentialAuthenticationService.authenticate(any(), any())).willReturn(account);
		doReturn(
			ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
				.body(new LoginResponse("access-token"))
		).when(authenticationStrategy).establish(any(), any());

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("access-token"))
			.andExpect(jsonPath("$.refreshToken").doesNotExist())
			.andExpect(cookie().value("refreshToken", "refresh-token"))
			.andExpect(cookie().httpOnly("refreshToken", true))
			.andExpect(cookie().secure("refreshToken", true))
			.andExpect(cookie().path("refreshToken", "/api/auth"))
			.andExpect(cookie().maxAge("refreshToken", 604800))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("Domain="))));
	}

	@Test
	void 로그인_정보가_틀리면_401이고_refresh_cookie를_발급하지_않는다() throws Exception {
		given(credentialAuthenticationService.authenticate(any(), any()))
			.willThrow(new InvalidCredentialsException());

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest()))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	@Test
	void 로그인_요청_형식이_잘못되면_400을_반환한다() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "not-an-email",
					  "password": ""
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}

	private String validRequest() {
		return """
			{
			  "email": "collector@example.com",
			  "password": "Password123!"
			}
			""";
	}
}
