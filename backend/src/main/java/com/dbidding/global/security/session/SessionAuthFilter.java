package com.dbidding.global.security.session;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dbidding.account.authentication.session.SessionPrincipal;
import com.dbidding.global.exception.UnauthorizedException;
import com.dbidding.global.security.RequestUserIdWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class SessionAuthFilter extends OncePerRequestFilter {

	private final RequestUserIdWriter requestUserIdWriter;

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
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}
}
