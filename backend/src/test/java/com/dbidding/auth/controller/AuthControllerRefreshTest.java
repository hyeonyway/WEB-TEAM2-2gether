package com.dbidding.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dbidding.auth.config.JwtProperties;
import com.dbidding.auth.cookie.RefreshCookieFactory;
import com.dbidding.auth.dto.RefreshResponse;
import com.dbidding.auth.exception.InvalidTokenException;
import com.dbidding.auth.service.AuthService;
import com.dbidding.auth.service.RefreshResult;

import jakarta.servlet.http.Cookie;

@WebMvcTest(AuthController.class)
@Import(RefreshCookieFactory.class)
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
	"app.jwt.secret=0123456789abcdef0123456789abcdef",
	"app.jwt.access-token-seconds=1800",
	"app.jwt.refresh-token-seconds=604800",
	"app.jwt.secure-cookie=true"
})
class AuthControllerRefreshTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@Test
	void refresh_cookie가_없으면_401과_에러_코드를_반환한다() throws Exception {
		mockMvc.perform(post("/api/auth/refresh"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_MISSING"))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

		then(authService).shouldHaveNoInteractions();
	}

	@Test
	void refresh_cookie가_비어_있으면_401과_에러_코드를_반환한다() throws Exception {
		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie("refreshToken", "")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_MISSING"))
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

		then(authService).shouldHaveNoInteractions();
	}

	@Test
	void refresh하면_access만_응답하고_회전된_refresh를_새_cookie로_전달한다() throws Exception {
		given(authService.refresh("old-refresh-token")).willReturn(new RefreshResult(
			new RefreshResponse("new-access-token"),
			"new-refresh-token"
		));

		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie("refreshToken", "old-refresh-token")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("new-access-token"))
			.andExpect(jsonPath("$.refreshToken").doesNotExist())
			.andExpect(cookie().value("refreshToken", "new-refresh-token"))
			.andExpect(cookie().httpOnly("refreshToken", true))
			.andExpect(cookie().secure("refreshToken", true))
			.andExpect(cookie().path("refreshToken", "/api/auth"))
			.andExpect(cookie().maxAge("refreshToken", 604800))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("Domain="))));
	}

	@Test
	void 유효하지_않은_refresh_token은_401이고_새_cookie를_발급하지_않는다() throws Exception {
		given(authService.refresh("invalid-refresh-token"))
			.willThrow(new InvalidTokenException());

		mockMvc.perform(post("/api/auth/refresh")
				.cookie(new Cookie("refreshToken", "invalid-refresh-token")))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
	}
}
