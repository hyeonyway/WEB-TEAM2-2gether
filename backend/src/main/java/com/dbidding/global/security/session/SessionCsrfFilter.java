package com.dbidding.global.security.session;

import java.io.IOException;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dbidding.account.authentication.session.SessionCsrfTokenService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Order(Ordered.HIGHEST_PRECEDENCE + 11)
@RequiredArgsConstructor
public class SessionCsrfFilter extends OncePerRequestFilter {

	public static final String CSRF_HEADER = "X-CSRF-Token";
	private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
	private static final Set<String> TOKEN_EXEMPT_PATHS = Set.of("/api/auth/login", "/api/auth/signup");

	private final SessionCsrfTokenService tokenService;

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (!requiresCsrfToken(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		HttpSession session = request.getSession(false);
		if (session == null || !tokenService.matches(session, request.getHeader(CSRF_HEADER))) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean requiresCsrfToken(HttpServletRequest request) {
		return UNSAFE_METHODS.contains(request.getMethod())
			&& !TOKEN_EXEMPT_PATHS.contains(request.getRequestURI());
	}
}
