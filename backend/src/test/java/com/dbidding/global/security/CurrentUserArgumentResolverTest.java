package com.dbidding.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;

@ExtendWith(MockitoExtension.class)
class CurrentUserArgumentResolverTest {

	@Mock
	private CurrentUserProvider currentUserProvider;

	private CurrentUserArgumentResolver resolver;

	@BeforeEach
	void setUp() {
		resolver = new CurrentUserArgumentResolver(currentUserProvider);
	}

	@Test
	void CurrentUser가_붙은_Integer_파라미터를_지원한다() {
		assertThat(resolver.supportsParameter(parameter("currentUser", Integer.class))).isTrue();
	}

	@Test
	void CurrentUser가_없는_파라미터는_지원하지_않는다() {
		assertThat(resolver.supportsParameter(parameter("plainUser", Integer.class))).isFalse();
	}

	@Test
	void CurrentUser가_붙어도_Integer가_아니면_지원하지_않는다() {
		assertThat(resolver.supportsParameter(parameter("wrongType", String.class))).isFalse();
	}

	@Test
	void 현재_사용자_Provider의_ID를_인자로_해석한다() throws Exception {
		given(currentUserProvider.getCurrentUserId()).willReturn(7);

		Object resolved = resolver.resolveArgument(
			parameter("currentUser", Integer.class),
			null,
			null,
			null
		);

		assertThat(resolved).isEqualTo(7);
	}

	private MethodParameter parameter(String methodName, Class<?> parameterType) {
		try {
			Method method = TestController.class.getDeclaredMethod(methodName, parameterType);
			return new MethodParameter(method, 0);
		} catch (NoSuchMethodException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static class TestController {

		void currentUser(@CurrentUser Integer userId) {
		}

		void plainUser(Integer userId) {
		}

		void wrongType(@CurrentUser String userId) {
		}
	}
}
