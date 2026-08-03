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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.account.authentication.jwt.JwtTokenProvider;
import com.dbidding.account.authentication.jwt.TokenClaims;
import com.dbidding.account.authentication.jwt.TokenType;
import com.dbidding.global.config.WebConfig;
import com.dbidding.global.exception.UnauthorizedException;
import com.dbidding.global.security.jwt.JwtAuthFilter;
import com.dbidding.global.security.jwt.SseTicketAuthFilter;
import com.dbidding.global.security.jwt.TicketProvider;

@WebMvcTest(SseTicketTestController.class)
@Import({
	WebConfig.class,
	RequestCurrentUserProvider.class,
	RequestUserIdWriter.class,
	JwtAuthFilter.class,
	SseTicketAuthFilter.class
})
class SseTicketCurrentUserWebMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TicketProvider ticketProvider;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void 유효한_SSE_티켓으로_CurrentUser를_주입한다() throws Exception {
		given(ticketProvider.validateAndConsume("valid-ticket")).willReturn(7);

		mockMvc.perform(get("/api/dashboard/stream")
				.param("ticket", "valid-ticket"))
			.andExpect(status().isOk())
			.andExpect(content().string("7"));
	}

	@Test
	void 유효하지_않은_SSE_티켓은_Controller에_도달하기_전에_401을_반환한다() throws Exception {
		given(ticketProvider.validateAndConsume("invalid-ticket"))
			.willThrow(new UnauthorizedException());

		mockMvc.perform(get("/api/dashboard/stream")
				.param("ticket", "invalid-ticket"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void JWT와_SSE_티켓의_사용자가_같으면_CurrentUser를_주입한다() throws Exception {
		given(jwtTokenProvider.parseAccess("access-token"))
			.willReturn(new TokenClaims(7, TokenType.ACCESS));
		given(ticketProvider.validateAndConsume("valid-ticket")).willReturn(7);

		mockMvc.perform(get("/api/dashboard/stream")
				.header("Authorization", "Bearer access-token")
				.param("ticket", "valid-ticket"))
			.andExpect(status().isOk())
			.andExpect(content().string("7"));
	}

	@Test
	void JWT와_SSE_티켓의_사용자가_다르면_401을_반환한다() throws Exception {
		given(jwtTokenProvider.parseAccess("access-token"))
			.willReturn(new TokenClaims(7, TokenType.ACCESS));
		given(ticketProvider.validateAndConsume("other-user-ticket")).willReturn(8);

		mockMvc.perform(get("/api/dashboard/stream")
				.header("Authorization", "Bearer access-token")
				.param("ticket", "other-user-ticket"))
			.andExpect(status().isUnauthorized());
	}
}

@RestController
class SseTicketTestController {

	@GetMapping("/api/dashboard/stream")
	Integer stream(@CurrentUser Integer userId) {
		return userId;
	}
}
