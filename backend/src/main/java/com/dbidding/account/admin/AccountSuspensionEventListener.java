package com.dbidding.account.admin;

import com.dbidding.account.event.AccountSuspendedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 세션/Redis/SSE 종료는 DB 트랜잭션과 무관한 I/O라서, 정지 트랜잭션의
 * PESSIMISTIC_WRITE 락을 붙잡은 채로 수행하지 않도록 커밋 이후로 미룬다.
 */
@Component
@RequiredArgsConstructor
public class AccountSuspensionEventListener {

	private final AccountSessionRevoker sessionRevoker;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void handle(AccountSuspendedEvent event) {
		sessionRevoker.revoke(event.accountId());
	}
}
