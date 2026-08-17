package com.dbidding.account.warning;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.dbidding.account.admin.AccountSuspensionService;
import com.dbidding.account.exception.AccountNotFoundException;
import com.dbidding.account.repository.AccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserWarningIssuer {

	private static final long WARNING_PERIOD_DAYS = 30;
	// 주문 없는 수동 경고는 order_id로 멱등성을 보장할 수 없어서, 짧은 시간 안의
	// 중복 요청(더블클릭, 네트워크 재시도, 여러 탭)을 대신 이 창으로 걸러낸다.
	private static final long MANUAL_WARNING_DEDUPE_SECONDS = 5;
	public static final long SUSPENSION_WARNING_COUNT = 3;

	private final AccountRepository accountRepository;
	private final UserWarningRepository userWarningRepository;
	private final AccountSuspensionService accountSuspensionService;
	private final Clock clock;

	@Transactional
	public void issue(Integer userId, Integer orderId, UserWarningReason reason) {
		accountRepository.findByIdForUpdate(userId)
			.orElseThrow(AccountNotFoundException::new);
		Instant issuedAt = clock.instant();
		if (isDuplicate(userId, orderId, reason, issuedAt)) return;

		userWarningRepository.save(UserWarning.issued(
			userId, orderId, reason, issuedAt, issuedAt.plus(WARNING_PERIOD_DAYS, ChronoUnit.DAYS)
		));
		if (userWarningRepository.countActiveByUserId(userId, issuedAt) >= SUSPENSION_WARNING_COUNT) {
			accountSuspensionService.suspendAutomatically(userId);
		}
	}

	private boolean isDuplicate(Integer userId, Integer orderId, UserWarningReason reason, Instant issuedAt) {
		if (orderId != null) {
			return userWarningRepository.existsByOrderIdAndReason(orderId, reason);
		}
		Instant dedupeWindowStart = issuedAt.minusSeconds(MANUAL_WARNING_DEDUPE_SECONDS);
		return userWarningRepository.existsByUserIdAndReasonAndIssuedAtAfter(userId, reason, dedupeWindowStart);
	}
}
