package com.dbidding.account.authentication.session;

import java.time.Instant;
import java.util.Optional;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.domain.AccountRole;

import jakarta.servlet.http.HttpSession;
import org.springframework.session.FindByIndexNameSessionRepository;

public record SessionPrincipal(
	Integer userId,
	String role,
	long authenticatedAt
) {

	public static final String USER_ID_ATTRIBUTE = "AUTHENTICATED_USER_ID";
	public static final String ROLE_ATTRIBUTE = "AUTHENTICATED_USER_ROLE";
	public static final String AUTHENTICATED_AT_ATTRIBUTE = "AUTHENTICATED_AT";

	public static SessionPrincipal authenticated(AuthenticatedAccount account, Instant authenticatedAt) {
		return new SessionPrincipal(
			account.userId(),
			account.role().name(),
			authenticatedAt.getEpochSecond()
		);
	}

	public static Optional<SessionPrincipal> readFrom(HttpSession session) {
		Object userId = session.getAttribute(USER_ID_ATTRIBUTE);
		Object role = session.getAttribute(ROLE_ATTRIBUTE);
		Object authenticatedAt = session.getAttribute(AUTHENTICATED_AT_ATTRIBUTE);
		if (!(userId instanceof Integer validUserId) || validUserId <= 0
			|| !(role instanceof String validRole) || !isKnownRole(validRole)
			|| !(authenticatedAt instanceof Long validAuthenticatedAt) || validAuthenticatedAt <= 0) {
			return Optional.empty();
		}
		return Optional.of(new SessionPrincipal(validUserId, validRole, validAuthenticatedAt));
	}

	public void writeTo(HttpSession session) {
		session.setAttribute(USER_ID_ATTRIBUTE, userId);
		session.setAttribute(ROLE_ATTRIBUTE, role);
		session.setAttribute(AUTHENTICATED_AT_ATTRIBUTE, authenticatedAt);
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, userId.toString());
	}

	private static boolean isKnownRole(String role) {
		try {
			AccountRole.valueOf(role);
			return true;
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}
}
