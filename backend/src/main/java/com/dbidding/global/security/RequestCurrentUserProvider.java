package com.dbidding.global.security;

import org.springframework.stereotype.Component;

import com.dbidding.global.exception.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RequestCurrentUserProvider implements CurrentUserProvider {

	static final String USER_ID_ATTRIBUTE = "userId";
	private static final String DEBUG_USER_ID_HEADER = "X-Debug-User-Id";

	private final HttpServletRequest request;

	@Override
	public Integer getCurrentUserId() {
		Object userId = request.getAttribute(USER_ID_ATTRIBUTE);
		if (userId instanceof Integer currentUserId && currentUserId > 0) {
			return currentUserId;
		}
		Integer debugUserId = parsePositiveUserId(request.getHeader(DEBUG_USER_ID_HEADER));
		if (debugUserId != null) {
			return debugUserId;
		}
		throw new UnauthorizedException();
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
