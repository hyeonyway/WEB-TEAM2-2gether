package com.dbidding.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.dbidding.global.exception.UnauthorizedException;

class RequestUserIdWriterTest {

	private final RequestUserIdWriter writer = new RequestUserIdWriter();

	@Test
	void 인증된_사용자_ID를_요청에_기록한다() {
		MockHttpServletRequest request = new MockHttpServletRequest();

		writer.write(request, 1);

		assertThat(request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE)).isEqualTo(1);
	}

	@Test
	void 동일한_사용자_ID를_다시_기록할_수_있다() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		writer.write(request, 1);

		writer.write(request, 1);

		assertThat(request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE)).isEqualTo(1);
	}

	@Test
	void 다른_인증_수단이_기록한_사용자와_충돌하면_거부한다() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		writer.write(request, 1);

		assertThatThrownBy(() -> writer.write(request, 2))
			.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void 보조_인증은_이미_기록된_사용자를_덮어쓰지_않는다() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		writer.write(request, 1);

		writer.writeIfAbsent(request, 2);

		assertThat(request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE)).isEqualTo(1);
	}
}
