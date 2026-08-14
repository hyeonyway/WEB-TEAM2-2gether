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

import com.dbidding.account.authentication.session.SessionPrincipal;
import com.dbidding.global.security.RequestUserIdWriter;
import com.dbidding.global.security.FilterErrorResponseWriter;
import tools.jackson.databind.ObjectMapper;

class SessionAuthFilterTest {

	private SessionAuthFilter filter;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		filter = new SessionAuthFilter(
			new RequestUserIdWriter(),
			new FilterErrorResponseWriter(objectMapper),
			new com.dbidding.account.authentication.session.SessionProperties(com.dbidding.account.authentication.session.SessionStore.MEMORY, "SESSION", false, "lax", java.time.Duration.ofHours(12)),
			java.time.Clock.fixed(java.time.Instant.ofEpochSecond(1_786_000_001L), java.time.ZoneOffset.UTC), org.mockito.Mockito.mock(SessionSseTerminationPublisher.class)
		);
	}

	@Test
	void 유효한_세션이면_userId를_request_attribute에_저장한다() throws Exception {
		MockHttpServletRequest request = authenticatedRequest(7);
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(request.getAttribute("userId")).isEqualTo(7);
		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	void 세션이_없으면_세션을_생성하지_않고_익명으로_통과시킨다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(request.getSession(false)).isNull();
		assertThat(request.getAttribute("userId")).isNull();
		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	void 세션_인증_속성의_타입이_잘못되면_익명으로_통과시킨다() throws Exception {
		MockHttpServletRequest request = authenticatedRequest(7);
		request.getSession(false).setAttribute(SessionPrincipal.USER_ID_ATTRIBUTE, "7");
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(request.getAttribute("userId")).isNull();
		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	void 다른_인증_수단의_userId와_충돌하면_401을_반환한다() throws Exception {
		MockHttpServletRequest request = authenticatedRequest(7);
		request.setAttribute("userId", 8);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
		var body = objectMapper.readTree(response.getContentAsString());
		assertThat(body.path("code").asText()).isEqualTo("UNAUTHORIZED");
		assertThat(body.path("message").asText()).isEqualTo("인증 정보가 일치하지 않습니다.");
		assertThat(request.getAttribute("userId")).isEqualTo(8);
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void Redis_세션을_읽을_수_있도록_Spring_Session_필터_이후에_실행된다() {
		Order order = SessionAuthFilter.class.getAnnotation(Order.class);

		assertThat(order.value()).isGreaterThan(SessionRepositoryFilter.DEFAULT_ORDER);
	}

	@Test
	void 활동_중이어도_절대_수명을_넘으면_세션을_폐기하고_401을_반환한다() throws Exception {
		var publisher = org.mockito.Mockito.mock(SessionSseTerminationPublisher.class);
		filter = new SessionAuthFilter(new RequestUserIdWriter(), new FilterErrorResponseWriter(objectMapper),
				new com.dbidding.account.authentication.session.SessionProperties(com.dbidding.account.authentication.session.SessionStore.MEMORY, "SESSION", false, "lax", java.time.Duration.ofHours(12)),
				java.time.Clock.fixed(java.time.Instant.ofEpochSecond(1_786_000_000L).plus(java.time.Duration.ofHours(12)), java.time.ZoneOffset.UTC), publisher);
		MockHttpServletRequest request = authenticatedRequest(7); MockHttpSession session = (MockHttpSession) request.getSession(false);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(401); assertThat(session.isInvalid()).isTrue();
		org.mockito.Mockito.verify(publisher).terminate(session.getId());
	}

	private MockHttpServletRequest authenticatedRequest(int userId) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpSession session = (MockHttpSession)request.getSession(true);
		session.setAttribute(SessionPrincipal.USER_ID_ATTRIBUTE, userId);
		session.setAttribute(SessionPrincipal.ROLE_ATTRIBUTE, "USER");
		session.setAttribute(SessionPrincipal.AUTHENTICATED_AT_ATTRIBUTE, 1_786_000_000L);
		return request;
	}
}
