package com.dbidding.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TestAuthFilterTest {

	private final TestAuthFilter filter = new TestAuthFilter();

	@Test
	void 양의_정수_디버그_ID를_request_attribute에_저장한다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Debug-User-Id", "7");

		filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(request.getAttribute("userId")).isEqualTo(7);
	}

	@Test
	void 디버그_헤더가_없으면_인증_정보를_저장하지_않는다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();

		filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(request.getAttribute("userId")).isNull();
	}

	@Test
	void 숫자가_아닌_디버그_ID는_인증_정보_없이_다음_필터로_전달한다() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Debug-User-Id", "invalid");
		MockFilterChain chain = new MockFilterChain();

		assertThatCode(() ->
			filter.doFilterInternal(request, new MockHttpServletResponse(), chain)
		).doesNotThrowAnyException();
		assertThat(request.getAttribute("userId")).isNull();
		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	void 영_이하의_디버그_ID는_인증_정보를_저장하지_않는다() throws Exception {
		MockHttpServletRequest zero = new MockHttpServletRequest();
		zero.addHeader("X-Debug-User-Id", "0");
		MockHttpServletRequest negative = new MockHttpServletRequest();
		negative.addHeader("X-Debug-User-Id", "-1");

		filter.doFilterInternal(zero, new MockHttpServletResponse(), new MockFilterChain());
		filter.doFilterInternal(negative, new MockHttpServletResponse(), new MockFilterChain());

		assertThat(zero.getAttribute("userId")).isNull();
		assertThat(negative.getAttribute("userId")).isNull();
	}
}
