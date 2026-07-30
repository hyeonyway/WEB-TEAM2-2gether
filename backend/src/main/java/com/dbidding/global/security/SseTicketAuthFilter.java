package com.dbidding.global.security;

import java.io.IOException;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dbidding.global.exception.UnauthorizedException;

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
		"/api/users/{userId}/notifications/stream"
	);

	private final TicketProvider ticketProvider;
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
			Object existingUserId = request.getAttribute(
				RequestCurrentUserProvider.USER_ID_ATTRIBUTE
			);
			if (existingUserId != null && !existingUserId.equals(userId)) {
				throw new UnauthorizedException();
			}
			request.setAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE, userId);
			filterChain.doFilter(request, response);
		} catch (UnauthorizedException exception) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}
}
