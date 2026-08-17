package com.dbidding.account.admin;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

import com.dbidding.account.authentication.session.SessionPrincipal;
import com.dbidding.global.security.session.SessionSseTerminationPublisher;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccountSessionRevoker {

	private final ObjectProvider<FindByIndexNameSessionRepository<?>> sessionRepositoryProvider;
	private final ObjectProvider<HttpServletRequest> requestProvider;
	private final SessionSseTerminationPublisher sessionSseTerminationPublisher;

	public void revoke(Integer userId) {
		invalidateCurrentTargetSession(userId);

		FindByIndexNameSessionRepository<?> repository = sessionRepositoryProvider.getIfAvailable();
		if (repository == null) return;
		repository.findByPrincipalName(userId.toString()).keySet().forEach(sessionId -> {
			sessionSseTerminationPublisher.terminate(sessionId);
			repository.deleteById(sessionId);
		});
	}

	private void invalidateCurrentTargetSession(Integer userId) {
		HttpServletRequest request = requestProvider.getIfAvailable();
		if (request == null) return;
		HttpSession session = request.getSession(false);
		if (session == null) return;
		SessionPrincipal.readFrom(session)
			.filter(principal -> principal.userId().equals(userId))
			.ifPresent(ignored -> session.invalidate());
	}
}
