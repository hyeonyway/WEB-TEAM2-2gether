package com.dbidding.global.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.web.http.SessionRepositoryFilter;

import com.dbidding.account.authentication.session.SessionCsrfTokenService;
import com.dbidding.global.security.FilterErrorResponseWriter;
import tools.jackson.databind.ObjectMapper;

class SessionCsrfFilterTest {

	private final SessionCsrfTokenService tokenService = new SessionCsrfTokenService();
	private SessionCsrfFilter filter;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		filter = new SessionCsrfFilter(tokenService, new FilterErrorResponseWriter(objectMapper));
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
		assertForbiddenError(response);
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

	@Test
	void 허용되지_않은_Origin의_상태_변경_요청은_CSRF_token이_있어도_403으로_거부한다() throws Exception {
		MockHttpServletRequest request = requestWithToken("POST", "/api/wallet/charges");
		request.addHeader("Origin", "https://attacker.example");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(403);
		assertForbiddenError(response);
	}

	@Test
	void cross_site_Fetch_Metadata의_로그인_요청은_403으로_거부한다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
		request.addHeader("Sec-Fetch-Site", "cross-site");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(403);
		assertForbiddenError(response);
	}

	@Test
	void 허용된_Origin의_로그인_요청은_통과시킨다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
		request.addHeader("Origin", "https://dbidding.shop");
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	void Redis_세션을_읽을_수_있도록_Spring_Session_필터_이후에_실행된다() {
		Order order = SessionCsrfFilter.class.getAnnotation(Order.class);

		assertThat(order.value()).isGreaterThan(SessionRepositoryFilter.DEFAULT_ORDER);
	}

	private MockHttpServletRequest requestWithToken(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		MockHttpSession session = new MockHttpSession();
		String token = tokenService.issue(session);
		request.setSession(session);
		request.addHeader(SessionCsrfFilter.CSRF_HEADER, token);
		return request;
	}

	private void assertForbiddenError(MockHttpServletResponse response) throws Exception {
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
		var body = objectMapper.readTree(response.getContentAsString());
		assertThat(body.path("code").asText()).isEqualTo("FORBIDDEN");
		assertThat(body.path("message").asText()).isEqualTo("요청이 허용되지 않았습니다.");
	}
}
