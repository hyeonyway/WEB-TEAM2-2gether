package com.dbidding.global.security.session;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dbidding.account.authentication.session.SessionPrincipal;
import com.dbidding.global.exception.UnauthorizedException;
import com.dbidding.global.security.FilterErrorResponseWriter;
import com.dbidding.global.security.RequestUserIdWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Order(SessionRepositoryFilter.DEFAULT_ORDER + 1)
@RequiredArgsConstructor
public class SessionAuthFilter extends OncePerRequestFilter {

	private static final String UNAUTHORIZED = "UNAUTHORIZED";
	private static final String UNAUTHORIZED_MESSAGE = "인증 정보가 일치하지 않습니다.";

	private final RequestUserIdWriter requestUserIdWriter;
	private final FilterErrorResponseWriter errorResponseWriter;

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session == null) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			SessionPrincipal.readFrom(session)
				.ifPresent(principal -> requestUserIdWriter.write(request, principal.userId()));
			filterChain.doFilter(request, response);
		} catch (UnauthorizedException exception) {
			errorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
		}
	}
}
