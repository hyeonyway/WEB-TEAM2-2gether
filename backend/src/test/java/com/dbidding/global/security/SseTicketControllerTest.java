package com.dbidding.global.security;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dbidding.global.security.jwt.SseTicketController;
import com.dbidding.global.security.jwt.TicketProvider;

import com.dbidding.global.exception.UnauthorizedException;

@WebMvcTest(SseTicketController.class)
class SseTicketControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TicketProvider ticketProvider;

	@MockitoBean
	private CurrentUserProvider currentUserProvider;

	@Test
	void 로그인_사용자에게_Provider가_정한_만료시간의_SSE_티켓을_발급한다() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(7);
		given(ticketProvider.issue(7)).willReturn("one-time-ticket");
		given(ticketProvider.ticketTtlSeconds()).willReturn(45L);

		mockMvc.perform(post("/api/sse/tickets"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ticket").value("one-time-ticket"))
			.andExpect(jsonPath("$.expiresInSeconds").value(45));

		then(ticketProvider).should().issue(7);
	}

	@Test
	void 인증되지_않은_사용자는_SSE_티켓을_발급받을_수_없다() throws Exception {
		given(currentUserProvider.getCurrentUserId())
			.willThrow(new UnauthorizedException());

		mockMvc.perform(post("/api/sse/tickets"))
			.andExpect(status().isUnauthorized());

		then(ticketProvider).shouldHaveNoInteractions();
	}
}
