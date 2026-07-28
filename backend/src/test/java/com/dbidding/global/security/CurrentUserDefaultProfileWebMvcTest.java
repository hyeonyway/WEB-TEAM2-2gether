package com.dbidding.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dbidding.global.config.WebConfig;

@ActiveProfiles("default")
@WebMvcTest(CurrentUserTestController.class)
@Import({
	WebConfig.class,
	RequestCurrentUserProvider.class,
	TestAuthFilter.class
})
class CurrentUserDefaultProfileWebMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void debug_auth_프로필이_없으면_디버그_헤더를_신뢰하지_않는다() throws Exception {
		mockMvc.perform(get("/test/current-user")
				.header("X-Debug-User-Id", "7"))
			.andExpect(status().isUnauthorized());
	}
}
