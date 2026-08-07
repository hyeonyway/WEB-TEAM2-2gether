package com.dbidding.account.authentication.session;

import java.time.Clock;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.authentication.AuthenticationStrategy;
import com.dbidding.account.dto.SessionLoginResponse;
import com.dbidding.global.security.session.SessionSseConnectionRegistry;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SessionAuthenticationStrategy implements AuthenticationStrategy {

	private final SessionProperties properties;
	private final Clock clock;
	private final SessionCsrfTokenService csrfTokenService;
	private final SessionSseConnectionRegistry sessionSseConnectionRegistry;

	@Override
	public ResponseEntity<?> establish(AuthenticatedAccount account, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			session = request.getSession(true);
		} else {
			request.changeSessionId();
		}

		SessionPrincipal.authenticated(account, clock.instant()).writeTo(session);
		String csrfToken = csrfTokenService.issue(session);
		return ResponseEntity.ok(new SessionLoginResponse(csrfToken));
	}

	@Override
	public ResponseEntity<Void> terminate(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			sessionSseConnectionRegistry.disconnect(session.getId());
			session.invalidate();
		}

		ResponseCookie expiredCookie = ResponseCookie.from(properties.cookieName(), "")
			.httpOnly(true)
			.secure(properties.secureCookie())
			.sameSite(properties.sameSite())
			.path("/")
			.maxAge(0)
			.build();
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
			.build();
	}
}
