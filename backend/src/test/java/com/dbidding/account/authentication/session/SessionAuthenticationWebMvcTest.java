package com.dbidding.account.authentication.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.authentication.CredentialAuthenticationService;
import com.dbidding.account.controller.AuthController;
import com.dbidding.account.dto.CurrentAccountResponse;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.domain.Account;
import com.dbidding.account.exception.InvalidCredentialsException;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.service.SignupService;
import com.dbidding.global.config.TimeConfig;
import com.dbidding.global.config.WebConfig;
import com.dbidding.global.security.CurrentUser;
import com.dbidding.global.security.RequestCurrentUserProvider;
import com.dbidding.global.security.RequestUserIdWriter;
import com.dbidding.global.security.session.SessionAuthConfiguration;
import com.dbidding.global.security.session.SessionCsrfController;
import com.dbidding.global.security.session.SessionSseConnectionRegistry;
import com.dbidding.global.security.session.SessionSseTerminationPublisher;
import com.dbidding.global.security.FilterErrorResponseWriter;

@WebMvcTest(
	controllers = {AuthController.class, SessionCurrentUserTestController.class},
	properties = {
		"app.session.store=memory",
		"app.session.cookie-name=SESSION",
		"app.session.secure-cookie=false"
	}
)
@Import({
	SessionAuthConfiguration.class,
	SessionSseConnectionRegistry.class,
	SessionCsrfController.class,
	TimeConfig.class,
	WebConfig.class,
	RequestCurrentUserProvider.class,
	RequestUserIdWriter.class,
	FilterErrorResponseWriter.class
})
class SessionAuthenticationWebMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SignupService signupService;

	@MockitoBean
	private CredentialAuthenticationService credentialAuthenticationService;

	@MockitoBean
	private AccountRepository accountRepository;

	@MockitoBean
	private com.dbidding.account.warning.UserWarningRepository userWarningRepository;

	@MockitoBean
	private SessionSseTerminationPublisher sessionSseTerminationPublisher;

	@Test
	void 로그인_세션으로_CurrentUser를_사용하고_로그아웃하면_세션과_쿠키를_폐기한다() throws Exception {
		given(credentialAuthenticationService.authenticate(any(), any()))
			.willReturn(new AuthenticatedAccount(7, AccountRole.USER));

		MvcResult login = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validLoginRequest()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.csrfToken").isNotEmpty())
			.andReturn();
		MockHttpSession session = (MockHttpSession)login.getRequest().getSession(false);

		assertThat(session).isNotNull();
		assertThat(Collections.list(session.getAttributeNames()))
			.containsExactlyInAnyOrder(
				SessionPrincipal.USER_ID_ATTRIBUTE,
				SessionPrincipal.ROLE_ATTRIBUTE,
				SessionPrincipal.AUTHENTICATED_AT_ATTRIBUTE,
				FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
				SessionCsrfTokenService.CSRF_TOKEN_ATTRIBUTE
			);

		mockMvc.perform(get("/test/session-current-user").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string("7"));

		mockMvc.perform(post("/api/auth/logout")
				.session(session)
				.header("X-CSRF-Token", session.getAttribute(SessionCsrfTokenService.CSRF_TOKEN_ATTRIBUTE)))
			.andExpect(status().isNoContent())
			.andExpect(cookie().value("SESSION", ""))
			.andExpect(cookie().path("SESSION", "/"))
			.andExpect(cookie().maxAge("SESSION", 0))
			.andExpect(cookie().httpOnly("SESSION", true));

		assertThat(session.isInvalid()).isTrue();
		mockMvc.perform(get("/test/session-current-user"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void 세션으로_현재_사용자와_CSRF_token을_조회한다() throws Exception {
		given(credentialAuthenticationService.authenticate(any(), any()))
			.willReturn(new AuthenticatedAccount(7, AccountRole.USER));

		MvcResult login = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validLoginRequest()))
			.andReturn();
		MockHttpSession session = (MockHttpSession)login.getRequest().getSession(false);
		Account account = org.mockito.Mockito.mock(Account.class);
		given(account.getRole()).willReturn(AccountRole.USER);
		given(accountRepository.findById(7)).willReturn(Optional.of(account));

		mockMvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value(7));
		mockMvc.perform(get("/api/auth/csrf").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.csrfToken").value(
				session.getAttribute(SessionCsrfTokenService.CSRF_TOKEN_ATTRIBUTE)
			));
	}

	@Test
	void 로그인에_실패하면_세션과_쿠키를_만들지_않는다() throws Exception {
		given(credentialAuthenticationService.authenticate(any(), any()))
			.willThrow(new InvalidCredentialsException());

		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validLoginRequest()))
			.andExpect(status().isUnauthorized())
			.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
			.andReturn();

		assertThat(result.getRequest().getSession(false)).isNull();
	}

	private String validLoginRequest() {
		return """
			{
			  "email": "collector@example.com",
			  "password": "Password123!"
			}
			""";
	}
}

@RestController
class SessionCurrentUserTestController {

	@GetMapping("/test/session-current-user")
	Integer currentUser(@CurrentUser Integer userId) {
		return userId;
	}
}
