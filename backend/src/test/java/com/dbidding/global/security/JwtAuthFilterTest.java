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
import com.dbidding.account.token.JwtTokenProvider;
import com.dbidding.account.token.TokenClaims;
import com.dbidding.account.token.TokenType;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	private JwtAuthFilter filter;

	@BeforeEach
	void setUp() {
		filter = new JwtAuthFilter(jwtTokenProvider, new RequestUserIdWriter());
	}

	@Test
	void 유효한_Access_Token이면_userId를_attribute에_저장한다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer valid-access-token");
		MockFilterChain chain = new MockFilterChain();
		given(jwtTokenProvider.parseAccess("valid-access-token"))
			.willReturn(new TokenClaims(7, TokenType.ACCESS));

		filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

		assertThat(request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE))
			.isEqualTo(7);
		assertThat(chain.getRequest()).isSameAs(request);
	}

	@Test
	void Authorization_헤더가_없으면_인증_정보_없이_통과시킨다() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

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

		filter.doFilterInternal(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
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

		filter.doFilterInternal(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(request.getAttribute(RequestCurrentUserProvider.USER_ID_ATTRIBUTE)).isNull();
		assertThat(chain.getRequest()).isNull();
	}
}
