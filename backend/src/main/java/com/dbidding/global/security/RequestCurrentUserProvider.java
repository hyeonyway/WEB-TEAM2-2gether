package com.dbidding.global.security;

import org.springframework.stereotype.Component;

import com.dbidding.global.exception.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RequestCurrentUserProvider implements CurrentUserProvider {

	static final String USER_ID_ATTRIBUTE = "userId";

	private final HttpServletRequest request;

	@Override
	public Integer getCurrentUserId() {
		Object userId = request.getAttribute(USER_ID_ATTRIBUTE);
		if (!(userId instanceof Integer currentUserId) || currentUserId <= 0) {
			throw new UnauthorizedException();
		}
		return currentUserId;
	}
}
