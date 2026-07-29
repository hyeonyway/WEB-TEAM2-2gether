package com.dbidding.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.global.config.WebConfig;

@ActiveProfiles("debug-auth")
@WebMvcTest(CurrentUserTestController.class)
@Import({
	WebConfig.class,
	RequestCurrentUserProvider.class,
	TestAuthFilter.class
})
class CurrentUserWebMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 디버그_헤더의_사용자_ID를_CurrentUser_인자로_주입한다() throws Exception {
		mockMvc.perform(get("/test/current-user")
				.header("X-Debug-User-Id", "7"))
			.andExpect(status().isOk())
			.andExpect(content().string("7"));
	}

	@Test
	void 디버그_헤더가_없으면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/test/current-user"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 잘못된_디버그_헤더는_500이_아닌_401을_반환한다() throws Exception {
		mockMvc.perform(get("/test/current-user")
				.header("X-Debug-User-Id", "invalid"))
			.andExpect(status().isUnauthorized());
	}

}

@RestController
class CurrentUserTestController {

	@GetMapping("/test/current-user")
	Integer currentUser(@CurrentUser Integer userId) {
		return userId;
	}
}
