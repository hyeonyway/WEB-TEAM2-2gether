package com.dbidding.global.security.jwt;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dbidding.account.exception.InvalidTokenException;
import com.dbidding.account.authentication.jwt.JwtTokenProvider;
import com.dbidding.account.authentication.jwt.TokenClaims;
import com.dbidding.global.exception.UnauthorizedException;
import com.dbidding.global.security.FilterErrorResponseWriter;
import com.dbidding.global.security.RequestUserIdWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";
	private static final String INVALID_TOKEN = "INVALID_TOKEN";
	private static final String INVALID_TOKEN_MESSAGE = "유효하지 않은 인증 토큰입니다.";
	private static final String UNAUTHORIZED = "UNAUTHORIZED";
	private static final String UNAUTHORIZED_MESSAGE = "인증 정보가 일치하지 않습니다.";

	private final JwtTokenProvider jwtTokenProvider;
	private final RequestUserIdWriter requestUserIdWriter;
	private final FilterErrorResponseWriter errorResponseWriter;

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String authorization = request.getHeader(AUTHORIZATION_HEADER);
		if (authorization == null) {
			filterChain.doFilter(request, response);
			return;
		}

		String accessToken = extractBearerToken(authorization);
		if (accessToken == null) {
			errorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, INVALID_TOKEN, INVALID_TOKEN_MESSAGE);
			return;
		}

		try {
			TokenClaims claims = jwtTokenProvider.parseAccess(accessToken);
			requestUserIdWriter.write(request, claims.userId());
			filterChain.doFilter(request, response);
		} catch (InvalidTokenException | UnauthorizedException exception) {
			String code = exception instanceof InvalidTokenException ? INVALID_TOKEN : UNAUTHORIZED;
			String message = exception instanceof InvalidTokenException ? INVALID_TOKEN_MESSAGE : UNAUTHORIZED_MESSAGE;
			errorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, code, message);
		}
	}

	private String extractBearerToken(String authorization) {
		if (!authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
			return null;
		}
		String token = authorization.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? null : token;
	}
}
