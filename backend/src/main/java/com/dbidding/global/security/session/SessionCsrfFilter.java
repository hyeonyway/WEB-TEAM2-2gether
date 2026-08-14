package com.dbidding.global.security.session;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dbidding.account.authentication.session.SessionCsrfTokenService;
import com.dbidding.global.security.FilterErrorResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Order(SessionRepositoryFilter.DEFAULT_ORDER + 2)
@RequiredArgsConstructor
public class SessionCsrfFilter extends OncePerRequestFilter {

	public static final String CSRF_HEADER = "X-CSRF-Token";
	private static final String ORIGIN_HEADER = "Origin";
	private static final String REFERER_HEADER = "Referer";
	private static final String FETCH_SITE_HEADER = "Sec-Fetch-Site";
	private static final Set<String> UNSAFE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
	private static final Set<String> TOKEN_EXEMPT_PATHS = Set.of("/api/auth/login", "/api/auth/signup");
	private static final String FORBIDDEN = "FORBIDDEN";
	private static final String FORBIDDEN_MESSAGE = "요청이 허용되지 않았습니다.";

	private final SessionCsrfTokenService tokenService;
	private final FilterErrorResponseWriter errorResponseWriter;

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (!isUnsafeMethod(request)) {
			filterChain.doFilter(request, response);
			return;
		}
		if (!isTrustedBrowserRequest(request)) {
			errorResponseWriter.write(response, HttpStatus.FORBIDDEN, FORBIDDEN, FORBIDDEN_MESSAGE);
			return;
		}
		if (!requiresCsrfToken(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		HttpSession session = request.getSession(false);
		if (session == null || !tokenService.matches(session, request.getHeader(CSRF_HEADER))) {
			errorResponseWriter.write(response, HttpStatus.FORBIDDEN, FORBIDDEN, FORBIDDEN_MESSAGE);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean requiresCsrfToken(HttpServletRequest request) {
		return !TOKEN_EXEMPT_PATHS.contains(request.getRequestURI());
	}

	private boolean isUnsafeMethod(HttpServletRequest request) {
		return UNSAFE_METHODS.contains(request.getMethod());
	}

	private boolean isTrustedBrowserRequest(HttpServletRequest request) {
		if ("cross-site".equalsIgnoreCase(request.getHeader(FETCH_SITE_HEADER))) {
			return false;
		}

		String origin = request.getHeader(ORIGIN_HEADER);
		if (origin != null) {
			return isAllowedOrigin(origin);
		}

		String referer = request.getHeader(REFERER_HEADER);
		return referer == null || isAllowedOrigin(originOf(referer));
	}

	private boolean isAllowedOrigin(String origin) {
		if ("https://dbidding.shop".equals(origin)) {
			return true;
		}
		try {
			URI uri = URI.create(origin);
			return "http".equals(uri.getScheme())
				&& ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private String originOf(String referer) {
		try {
			URI uri = URI.create(referer);
			return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), null, null, null).toString();
		} catch (Exception ignored) {
			return "";
		}
	}
}
