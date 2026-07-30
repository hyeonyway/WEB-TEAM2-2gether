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

import com.dbidding.global.config.WebConfig;
import com.dbidding.global.exception.UnauthorizedException;

@WebMvcTest(SseTicketTestController.class)
@Import({
	WebConfig.class,
	RequestCurrentUserProvider.class,
	SseTicketAuthFilter.class
})
class SseTicketCurrentUserWebMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TicketProvider ticketProvider;

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
}

@RestController
class SseTicketTestController {

	@GetMapping("/api/dashboard/stream")
	Integer stream(@CurrentUser Integer userId) {
		return userId;
	}
}
