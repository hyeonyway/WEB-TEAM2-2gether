package com.dbidding.global.security;

import org.springframework.stereotype.Component;

import com.dbidding.global.exception.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RequestUserIdWriter {

	public void write(HttpServletRequest request, Integer userId) {
		Object existingUserId = request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE);
		if (existingUserId != null && !existingUserId.equals(userId)) {
			throw new UnauthorizedException();
		}
		request.setAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE, userId);
	}

	public void writeIfAbsent(HttpServletRequest request, Integer userId) {
		if (request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE) == null) {
			request.setAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE, userId);
		}
	}
}
