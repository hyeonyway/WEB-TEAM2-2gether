package com.dbidding.account.authentication.session;

import java.time.Clock;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.dto.SessionLoginResponse;
import com.dbidding.global.security.session.SessionSseTerminationPublisher;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SessionAuthenticationStrategy {

	private final SessionProperties properties;
	private final Clock clock;
	private final SessionCsrfTokenService csrfTokenService;
	private final SessionSseTerminationPublisher sessionSseTerminationPublisher;
	private final ObjectProvider<FindByIndexNameSessionRepository<?>> sessionRepositoryProvider;

	public ResponseEntity<?> establish(AuthenticatedAccount account, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			session = request.getSession(true);
		} else {
			request.changeSessionId();
		}
		invalidateExistingSessions(account.userId(), session.getId());

		SessionPrincipal.authenticated(account, clock.instant()).writeTo(session);
		String csrfToken = csrfTokenService.issue(session);
		return ResponseEntity.ok(new SessionLoginResponse(csrfToken));
	}

	private void invalidateExistingSessions(Integer userId, String currentSessionId) {
		FindByIndexNameSessionRepository<?> repository = sessionRepositoryProvider.getIfAvailable();
		if (repository == null) return;
		repository.findByPrincipalName(userId.toString()).keySet().stream().filter(sessionId -> !sessionId.equals(currentSessionId)).forEach(sessionId -> {
			sessionSseTerminationPublisher.terminate(sessionId);
			repository.deleteById(sessionId);
		});
	}

	public ResponseEntity<Void> terminate(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			sessionSseTerminationPublisher.terminate(session.getId());
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
