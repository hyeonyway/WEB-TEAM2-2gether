package com.dbidding.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.dbidding.account.exception.InvalidTokenException;
import com.dbidding.account.authentication.jwt.JwtTokenProvider;
import com.dbidding.account.authentication.jwt.TokenClaims;
import com.dbidding.account.authentication.jwt.TokenType;
import com.dbidding.global.security.jwt.JwtAuthFilter;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	private JwtAuthFilter filter;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		filter = new JwtAuthFilter(
			jwtTokenProvider,
			new RequestUserIdWriter(),
			new FilterErrorResponseWriter(objectMapper)
		);
	}

	@Test
	void 유효한_Access_Token이면_userId를_attribute에_저장한다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer valid-access-token");
		MockFilterChain chain = new MockFilterChain();
		given(jwtTokenProvider.parseAccess("valid-access-token"))
			.willReturn(new TokenClaims(7, TokenType.ACCESS));

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE))
			.isEqualTo(7);
		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	void Authorization_헤더가_없으면_인증_정보_없이_통과시킨다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, new MockHttpServletResponse(), chain);

		assertThat(request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE)).isNull();
		assertThat(chain.getRequest()).isSameAs(request);
		then(jwtTokenProvider).shouldHaveNoInteractions();
	}

	@Test
	void Bearer가_아닌_Authorization_헤더는_401을_반환한다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Basic credentials");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertError(response, "INVALID_TOKEN", "유효하지 않은 인증 토큰입니다.");
		assertThat(chain.getRequest()).isNull();
		then(jwtTokenProvider).should(never()).parseAccess("credentials");
	}

	@Test
	void 유효하지_않은_Bearer_Token은_401을_반환한다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer invalid-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		given(jwtTokenProvider.parseAccess("invalid-token"))
			.willThrow(new InvalidTokenException());

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertError(response, "INVALID_TOKEN", "유효하지 않은 인증 토큰입니다.");
		assertThat(request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE)).isNull();
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void 다른_인증_수단의_userId와_충돌하면_401을_반환한다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer valid-access-token");
		request.setAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE, 8);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		given(jwtTokenProvider.parseAccess("valid-access-token"))
			.willReturn(new TokenClaims(7, TokenType.ACCESS));

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertError(response, "UNAUTHORIZED", "인증 정보가 일치하지 않습니다.");
		assertThat(request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE)).isEqualTo(8);
		assertThat(chain.getRequest()).isNull();
	}

	private void assertError(MockHttpServletResponse response, String code, String message) throws Exception {
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
		var body = objectMapper.readTree(response.getContentAsString());
		assertThat(body.path("code").asText()).isEqualTo(code);
		assertThat(body.path("message").asText()).isEqualTo(message);
	}
}
