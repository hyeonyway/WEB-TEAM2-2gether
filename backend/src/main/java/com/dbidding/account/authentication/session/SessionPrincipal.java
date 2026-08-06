package com.dbidding.account.authentication.session;

import java.time.Instant;

import com.dbidding.account.authentication.AuthenticatedAccount;

import jakarta.servlet.http.HttpSession;

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

	public void writeTo(HttpSession session) {
		session.setAttribute(USER_ID_ATTRIBUTE, userId);
		session.setAttribute(ROLE_ATTRIBUTE, role);
		session.setAttribute(AUTHENTICATED_AT_ATTRIBUTE, authenticatedAt);
	}
}
