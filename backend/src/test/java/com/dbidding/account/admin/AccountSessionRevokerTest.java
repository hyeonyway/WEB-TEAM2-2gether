package com.dbidding.account.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.FindByIndexNameSessionRepository;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.authentication.session.SessionPrincipal;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.global.security.session.SessionSseTerminationPublisher;

@SuppressWarnings({"rawtypes", "unchecked"})
class AccountSessionRevokerTest {

	@Test
	void 현재_대상_사용자의_세션을_먼저_무효화하고_모든_저장_세션을_종료한다() {
		FindByIndexNameSessionRepository repository = org.mockito.Mockito.mock(FindByIndexNameSessionRepository.class);
		ObjectProvider repositoryProvider = org.mockito.Mockito.mock(ObjectProvider.class);
		when(repositoryProvider.getIfAvailable()).thenReturn(repository);
		SessionSseTerminationPublisher terminationPublisher = org.mockito.Mockito.mock(SessionSseTerminationPublisher.class);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpSession session = (MockHttpSession) request.getSession(true);
		SessionPrincipal.authenticated(new AuthenticatedAccount(7, AccountRole.USER), java.time.Instant.now()).writeTo(session);
		ObjectProvider<jakarta.servlet.http.HttpServletRequest> requestProvider = org.mockito.Mockito.mock(ObjectProvider.class);
		when(requestProvider.getIfAvailable()).thenReturn(request);
		when(repository.findByPrincipalName("7")).thenReturn(Map.of(
			session.getId(), org.mockito.Mockito.mock(org.springframework.session.Session.class),
			"other-session", org.mockito.Mockito.mock(org.springframework.session.Session.class)
		));

		new AccountSessionRevoker(repositoryProvider, requestProvider, terminationPublisher).revoke(7);

		assertThat(session.isInvalid()).isTrue();
		verify(terminationPublisher).terminate(session.getId());
		verify(terminationPublisher).terminate("other-session");
		verify(repository).deleteById(session.getId());
		verify(repository).deleteById("other-session");
	}
}
