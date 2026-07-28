package com.dbidding.global.security;

import java.io.IOException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Profile("debug-auth")
public class TestAuthFilter extends OncePerRequestFilter {

	private static final String DEBUG_USER_ID_HEADER = "X-Debug-User-Id";

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		Integer userId = parsePositiveUserId(request.getHeader(DEBUG_USER_ID_HEADER));
		if (userId != null) {
			request.setAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE, userId);
		}
		filterChain.doFilter(request, response);
	}

	private Integer parsePositiveUserId(String value) {
		if (value == null) {
			return null;
		}
		try {
			int userId = Integer.parseInt(value);
			return userId > 0 ? userId : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}
}
