package com.dbidding.account.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dbidding.account.config.JwtProperties;
import com.dbidding.account.authentication.AuthenticationStrategy;
import com.dbidding.account.authentication.CredentialAuthenticationService;
import com.dbidding.account.cookie.RefreshCookieFactory;
import com.dbidding.account.service.AuthService;

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
class AuthControllerLogoutTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private CredentialAuthenticationService credentialAuthenticationService;

	@MockitoBean
	private AuthenticationStrategy authenticationStrategy;

	@Test
	void 로그아웃하면_서버_인증_정보를_폐기하고_refresh_cookie를_만료시킨다() throws Exception {
		givenLogoutResponse();
		mockMvc.perform(post("/api/auth/logout")
				.cookie(new Cookie("refreshToken", "refresh-token")))
			.andExpect(status().isNoContent())
			.andExpect(cookie().value("refreshToken", ""))
			.andExpect(cookie().httpOnly("refreshToken", true))
			.andExpect(cookie().secure("refreshToken", true))
			.andExpect(cookie().path("refreshToken", "/api/auth"))
			.andExpect(cookie().maxAge("refreshToken", 0))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("Domain="))));

		then(authenticationStrategy).should().terminate(any());
	}

	@Test
	void refresh_cookie가_없어도_로그아웃은_204이고_만료_cookie를_반환한다() throws Exception {
		givenLogoutResponse();
		mockMvc.perform(post("/api/auth/logout"))
			.andExpect(status().isNoContent())
			.andExpect(cookie().value("refreshToken", ""))
			.andExpect(cookie().maxAge("refreshToken", 0));

		then(authenticationStrategy).should().terminate(any());
	}

	private void givenLogoutResponse() {
		ResponseCookie expired = ResponseCookie.from("refreshToken", "")
			.httpOnly(true)
			.secure(true)
			.path("/api/auth")
			.maxAge(0)
			.sameSite("Strict")
			.build();
		given(authenticationStrategy.terminate(any())).willReturn(
			ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, expired.toString())
				.build()
		);
	}
}
