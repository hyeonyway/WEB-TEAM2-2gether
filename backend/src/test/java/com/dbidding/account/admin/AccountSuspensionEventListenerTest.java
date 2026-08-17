package com.dbidding.account.admin;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.dbidding.account.event.AccountSuspendedEvent;

class AccountSuspensionEventListenerTest {

	@Test
	void 정지_이벤트를_받으면_대상_세션을_종료한다() {
		AccountSessionRevoker sessionRevoker = org.mockito.Mockito.mock(AccountSessionRevoker.class);
		AccountSuspensionEventListener listener = new AccountSuspensionEventListener(sessionRevoker);

		listener.handle(new AccountSuspendedEvent(7));

		verify(sessionRevoker).revoke(7);
	}

	@Test
	void 세션_종료는_커밋_이후에_트랜잭션_밖_이벤트도_처리한다() throws Exception {
		TransactionalEventListener listener = AccountSuspensionEventListener.class
			.getMethod("handle", AccountSuspendedEvent.class)
			.getAnnotation(TransactionalEventListener.class);

		org.assertj.core.api.Assertions.assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
		org.assertj.core.api.Assertions.assertThat(listener.fallbackExecution()).isTrue();
	}
}
