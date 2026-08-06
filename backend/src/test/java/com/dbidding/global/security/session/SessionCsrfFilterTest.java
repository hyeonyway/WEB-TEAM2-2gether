package com.dbidding.global.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import com.dbidding.account.authentication.session.SessionCsrfTokenService;

class SessionCsrfFilterTest {

	private final SessionCsrfTokenService tokenService = new SessionCsrfTokenService();
	private SessionCsrfFilter filter;

	@BeforeEach
	void setUp() {
		filter = new SessionCsrfFilter(tokenService);
	}

	@Test
	void 유효한_세션_CSRF_token이_있으면_상태_변경_요청을_통과시킨다() throws Exception {
		MockHttpServletRequest request = requestWithToken("POST", "/api/wallet/charges");
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	void CSRF_token이_없으면_상태_변경_요청을_403으로_거부한다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/wallet/charges");
		request.setSession(new MockHttpSession());
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(403);
	}

	@Test
	void GET_요청과_로그인_요청은_CSRF_token_없이_통과시킨다() throws Exception {
		MockHttpServletRequest getRequest = new MockHttpServletRequest("GET", "/api/wallet");
		MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/api/auth/login");
		MockFilterChain getChain = new MockFilterChain();
		MockFilterChain loginChain = new MockFilterChain();

		filter.doFilter(getRequest, new MockHttpServletResponse(), getChain);
		filter.doFilter(loginRequest, new MockHttpServletResponse(), loginChain);

		assertThat(getChain.getRequest()).isSameAs(getRequest);
		assertThat(loginChain.getRequest()).isSameAs(loginRequest);
	}

	private MockHttpServletRequest requestWithToken(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		MockHttpSession session = new MockHttpSession();
		String token = tokenService.issue(session);
		request.setSession(session);
		request.addHeader(SessionCsrfFilter.CSRF_HEADER, token);
		return request;
	}
}
