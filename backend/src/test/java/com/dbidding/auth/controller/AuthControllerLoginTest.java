package com.dbidding.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dbidding.auth.config.JwtProperties;
import com.dbidding.auth.cookie.RefreshCookieFactory;
import com.dbidding.auth.dto.LoginRequest;
import com.dbidding.auth.dto.LoginResponse;
import com.dbidding.auth.exception.InvalidCredentialsException;
import com.dbidding.auth.service.AuthService;
import com.dbidding.auth.service.LoginResult;

@WebMvcTest(AuthController.class)
@Import(RefreshCookieFactory.class)
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
	"app.jwt.secret=0123456789abcdef0123456789abcdef",
	"app.jwt.access-token-seconds=1800",
	"app.jwt.refresh-token-seconds=604800",
	"app.jwt.secure-cookie=true"
})
class AuthControllerLoginTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@Test
	void 로그인하면_access만_응답하고_refresh는_host_only_cookie로_전달한다() throws Exception {
		given(authService.login(any(LoginRequest.class))).willReturn(new LoginResult(
			new LoginResponse("access-token"),
			"refresh-token"
		));

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
		given(authService.login(any(LoginRequest.class)))
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
