package com.dbidding.global.security.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dbidding.global.exception.UnauthorizedException;
import com.dbidding.global.security.FilterErrorResponseWriter;
import com.dbidding.global.security.RequestUserIdWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Order(Ordered.HIGHEST_PRECEDENCE + 15)
@RequiredArgsConstructor
public class SseTicketAuthFilter extends OncePerRequestFilter {

	private static final List<String> PERSONALIZED_SSE_PATHS = List.of(
		"/api/dashboard/stream",
		"/api/users/{userId}/auctions/stream",
		"/api/users/{userId}/notifications/stream",
		"/api/me/wallet/stream"
	);
	private static final String UNAUTHORIZED = "UNAUTHORIZED";
	private static final String UNAUTHORIZED_MESSAGE = "인증이 필요합니다.";

	private final TicketProvider ticketProvider;
	private final RequestUserIdWriter requestUserIdWriter;
	private final FilterErrorResponseWriter errorResponseWriter;
	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return PERSONALIZED_SSE_PATHS.stream()
			.noneMatch(pattern -> pathMatcher.match(pattern, path));
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		try {
			Integer userId = ticketProvider.validateAndConsume(request.getParameter("ticket"));
			requestUserIdWriter.write(request, userId);
			filterChain.doFilter(request, response);
		} catch (UnauthorizedException exception) {
			errorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
		}
	}
}
