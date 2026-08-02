package com.dbidding.global.security;

import com.dbidding.global.exception.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

	private final CurrentUserProvider currentUserProvider;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentUser.class)
			&& parameter.getParameterType().equals(Integer.class);
	}

	@Override
	public Object resolveArgument(
		MethodParameter parameter,
		ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest,
		WebDataBinderFactory binderFactory
	) {
		CurrentUser currentUser = parameter.getParameterAnnotation(CurrentUser.class);
		try {
			return currentUserProvider.getCurrentUserId();
		} catch (UnauthorizedException exception) {
			if (currentUser != null && !currentUser.required()) {
				return null;
			}
			throw exception;
		}
	}
}
