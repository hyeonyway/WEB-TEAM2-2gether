package com.dbidding.global.security.session;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.account.dto.SessionLoginResponse;
import com.dbidding.account.authentication.session.SessionCsrfTokenService;
import com.dbidding.global.security.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/csrf")
@RequiredArgsConstructor
public class SessionCsrfController {

	private final SessionCsrfTokenService tokenService;

	@GetMapping
	public SessionLoginResponse csrfToken(@CurrentUser Integer userId, HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			throw new IllegalStateException("Authenticated session is required");
		}
		Object token = session.getAttribute(SessionCsrfTokenService.CSRF_TOKEN_ATTRIBUTE);
		return new SessionLoginResponse(token instanceof String value ? value : tokenService.issue(session));
	}
}
