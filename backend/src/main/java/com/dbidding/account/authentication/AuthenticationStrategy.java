package com.dbidding.account.authentication;

import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthenticationStrategy {

	ResponseEntity<?> establish(AuthenticatedAccount account, HttpServletRequest request);

	ResponseEntity<Void> terminate(HttpServletRequest request);
}
