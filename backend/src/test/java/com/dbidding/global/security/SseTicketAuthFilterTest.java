package com.dbidding.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.dbidding.global.exception.UnauthorizedException;

@ExtendWith(MockitoExtension.class)
class SseTicketAuthFilterTest {

	@Mock
	private TicketProvider ticketProvider;

	private SseTicketAuthFilter filter;

	@BeforeEach
	void setUp() {
		filter = new SseTicketAuthFilter(ticketProvider);
	}

	@Test
	void 유효한_티켓이면_개인화_SSE_요청에_userId를_저장한다() throws Exception {
		MockHttpServletRequest request = get("/api/dashboard/stream");
		request.setParameter("ticket", "valid-ticket");
		MockFilterChain chain = new MockFilterChain();
		given(ticketProvider.validateAndConsume("valid-ticket")).willReturn(7);

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE))
			.isEqualTo(7);
		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	void 기존_사용자와_티켓_사용자가_같으면_개인화_SSE_요청을_통과시킨다() throws Exception {
		MockHttpServletRequest request = get("/api/dashboard/stream");
		request.setAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE, 7);
		request.setParameter("ticket", "valid-ticket");
		MockFilterChain chain = new MockFilterChain();
		given(ticketProvider.validateAndConsume("valid-ticket")).willReturn(7);

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE))
			.isEqualTo(7);
		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	void 기존_사용자와_티켓_사용자가_다르면_티켓을_소비하고_401을_반환한다() throws Exception {
		InMemoryTicketProvider realTicketProvider = new InMemoryTicketProvider(
			Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), ZoneOffset.UTC)
		);
		SseTicketAuthFilter realFilter = new SseTicketAuthFilter(realTicketProvider);
		String ticket = realTicketProvider.issue(8);
		MockHttpServletRequest request = get("/api/dashboard/stream");
		request.setAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE, 7);
		request.setParameter("ticket", ticket);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		realFilter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE))
			.isEqualTo(7);
		assertThat(chain.getRequest()).isNull();
		assertThatThrownBy(() -> realTicketProvider.validateAndConsume(ticket))
			.isInstanceOf(UnauthorizedException.class);
	}

	@ParameterizedTest
	@MethodSource("personalizedSsePaths")
	void 개인화_SSE_경로는_티켓_인증을_적용한다(String path) throws Exception {
		MockHttpServletRequest request = get(path);
		request.setParameter("ticket", "valid-ticket");
		given(ticketProvider.validateAndConsume("valid-ticket")).willReturn(7);

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		then(ticketProvider).should().validateAndConsume("valid-ticket");
	}

	@Test
	void 티켓이_없거나_유효하지_않으면_401을_반환한다() throws Exception {
		MockHttpServletRequest request = get("/api/dashboard/stream");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		given(ticketProvider.validateAndConsume(null))
			.willThrow(new UnauthorizedException());

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(chain.getRequest()).isNull();
	}

	@ParameterizedTest
	@MethodSource("publicOrNonSsePaths")
	void 공개_SSE와_일반_API에는_티켓_인증을_적용하지_않는다(String path) throws Exception {
		MockHttpServletRequest request = get(path);
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(chain.getRequest()).isSameAs(request);
		then(ticketProvider).shouldHaveNoInteractions();
	}

	private static Stream<Arguments> personalizedSsePaths() {
		return Stream.of(
			Arguments.of("/api/dashboard/stream"),
			Arguments.of("/api/users/7/auctions/stream"),
			Arguments.of("/api/users/7/notifications/stream")
		);
	}

	private static Stream<Arguments> publicOrNonSsePaths() {
		return Stream.of(
			Arguments.of("/api/auctions/stream"),
			Arguments.of("/api/auctions/1/stream"),
			Arguments.of("/api/wallet")
		);
	}

	private MockHttpServletRequest get(String path) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.setRequestURI(path);
		return request;
	}
}
