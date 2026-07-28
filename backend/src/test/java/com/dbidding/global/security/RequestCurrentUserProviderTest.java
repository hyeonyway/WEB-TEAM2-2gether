package com.dbidding.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.dbidding.global.exception.UnauthorizedException;

class RequestCurrentUserProviderTest {

	@Test
	void request_attribute의_사용자_ID를_반환한다() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setAttribute("userId", 7);
		RequestCurrentUserProvider provider = new RequestCurrentUserProvider(request);

		assertThat(provider.getCurrentUserId()).isEqualTo(7);
	}

	@Test
	void 사용자_ID가_없으면_인증_실패로_처리한다() {
		RequestCurrentUserProvider provider =
			new RequestCurrentUserProvider(new MockHttpServletRequest());

		assertThatThrownBy(provider::getCurrentUserId)
			.isInstanceOf(UnauthorizedException.class);
	}
}
