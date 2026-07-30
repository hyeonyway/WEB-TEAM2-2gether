package com.dbidding.global.security;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dbidding.auth.exception.InvalidTokenException;
import com.dbidding.auth.token.JwtTokenProvider;
import com.dbidding.auth.token.TokenClaims;
import com.dbidding.auth.token.TokenType;
import com.dbidding.global.config.WebConfig;

@WebMvcTest(CurrentUserTestController.class)
@Import({
	WebConfig.class,
	RequestCurrentUserProvider.class,
	JwtAuthFilter.class
})
class JwtCurrentUserWebMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void 유효한_Access_Token으로_CurrentUser를_주입한다() throws Exception {
		given(jwtTokenProvider.parseAccess("valid-access-token"))
			.willReturn(new TokenClaims(7, TokenType.ACCESS));

		mockMvc.perform(get("/test/current-user")
				.header("Authorization", "Bearer valid-access-token"))
			.andExpect(status().isOk())
			.andExpect(content().string("7"));
	}

	@Test
	void 유효하지_않은_Access_Token은_401을_반환한다() throws Exception {
		given(jwtTokenProvider.parseAccess("invalid-access-token"))
			.willThrow(new InvalidTokenException());

		mockMvc.perform(get("/test/current-user")
				.header("Authorization", "Bearer invalid-access-token"))
			.andExpect(status().isUnauthorized());
	}
}
