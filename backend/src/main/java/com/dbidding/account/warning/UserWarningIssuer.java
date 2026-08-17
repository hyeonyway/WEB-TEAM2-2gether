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
	public static final long SUSPENSION_WARNING_COUNT = 3;

	private final AccountRepository accountRepository;
	private final UserWarningRepository userWarningRepository;
	private final AccountSuspensionService accountSuspensionService;
	private final Clock clock;

	@Transactional
	public void issue(Integer userId, Integer orderId, UserWarningReason reason) {
		accountRepository.findByIdForUpdate(userId)
			.orElseThrow(AccountNotFoundException::new);
		if (orderId != null && userWarningRepository.existsByOrderIdAndReason(orderId, reason)) return;

		Instant issuedAt = clock.instant();
		userWarningRepository.save(UserWarning.issued(
			userId, orderId, reason, issuedAt, issuedAt.plus(WARNING_PERIOD_DAYS, ChronoUnit.DAYS)
		));
		if (userWarningRepository.countActiveByUserId(userId, issuedAt) >= SUSPENSION_WARNING_COUNT) {
			accountSuspensionService.suspendAutomatically(userId);
		}
	}
}
