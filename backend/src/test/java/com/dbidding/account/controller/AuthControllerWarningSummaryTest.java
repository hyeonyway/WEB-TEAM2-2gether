package com.dbidding.account.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dbidding.account.authentication.CredentialAuthenticationService;
import com.dbidding.account.authentication.session.SessionAuthenticationStrategy;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.service.SignupService;
import com.dbidding.account.warning.UserWarningIssuer;
import com.dbidding.account.warning.UserWarningRepository;
import com.dbidding.global.security.CurrentUserProvider;

@WebMvcTest(AuthController.class)
class AuthControllerWarningSummaryTest {

	private static final Integer USER_ID = 7;
	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SignupService signupService;

	@MockitoBean
	private CredentialAuthenticationService credentialAuthenticationService;

	@MockitoBean
	private SessionAuthenticationStrategy sessionAuthenticationStrategy;

	@MockitoBean
	private AccountRepository accountRepository;

	@MockitoBean
	private UserWarningRepository userWarningRepository;

	@MockitoBean
	private Clock clock;

	@MockitoBean
	private CurrentUserProvider currentUserProvider;

	@Test
	void 본인의_활성_경고_수와_정지_기준을_조회한다() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(USER_ID);
		given(clock.instant()).willReturn(NOW);
		given(userWarningRepository.countActiveByUserId(USER_ID, NOW)).willReturn(2L);

		mockMvc.perform(get("/api/auth/me/warnings"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active_warning_count").value(2))
			.andExpect(jsonPath("$.suspension_threshold").value(UserWarningIssuer.SUSPENSION_WARNING_COUNT));
	}
}
