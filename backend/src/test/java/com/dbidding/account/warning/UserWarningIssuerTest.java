package com.dbidding.account.warning;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.dbidding.account.admin.AccountSuspensionService;
import com.dbidding.account.domain.Account;
import com.dbidding.account.repository.AccountRepository;

class UserWarningIssuerTest {

	@Test
	void 중복_취소_이벤트는_새_경고나_자동_정지를_만들지_않는다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		UserWarningRepository warnings = org.mockito.Mockito.mock(UserWarningRepository.class);
		AccountSuspensionService suspensionService = org.mockito.Mockito.mock(AccountSuspensionService.class);
		Account account = org.mockito.Mockito.mock(Account.class);
		given(accounts.findByIdForUpdate(7)).willReturn(Optional.of(account));
		given(warnings.existsByOrderIdAndReason(10, UserWarningReason.BUYER_CANCELLED)).willReturn(true);

		new UserWarningIssuer(accounts, warnings, suspensionService, Clock.systemUTC())
			.issue(7, 10, UserWarningReason.BUYER_CANCELLED);

		verify(warnings, never()).save(org.mockito.Mockito.any());
		verify(suspensionService, never()).suspendAutomatically(7);
	}

	@Test
	void 세번째_활성_경고는_대상_계정을_자동_정지한다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		UserWarningRepository warnings = org.mockito.Mockito.mock(UserWarningRepository.class);
		AccountSuspensionService suspensionService = org.mockito.Mockito.mock(AccountSuspensionService.class);
		Account account = org.mockito.Mockito.mock(Account.class);
		Instant now = Instant.parse("2026-08-15T00:00:00Z");
		given(accounts.findByIdForUpdate(7)).willReturn(Optional.of(account));
		given(warnings.existsByOrderIdAndReason(10, UserWarningReason.BUYER_CANCELLED)).willReturn(false);
		given(warnings.countActiveByUserId(7, now)).willReturn(3L);

		new UserWarningIssuer(accounts, warnings, suspensionService, Clock.fixed(now, ZoneOffset.UTC))
			.issue(7, 10, UserWarningReason.BUYER_CANCELLED);

		verify(warnings).save(org.mockito.Mockito.any(UserWarning.class));
		verify(suspensionService).suspendAutomatically(7);
	}

	@Test
	void 두번째_활성_경고는_아직_자동_정지하지_않는다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		UserWarningRepository warnings = org.mockito.Mockito.mock(UserWarningRepository.class);
		AccountSuspensionService suspensionService = org.mockito.Mockito.mock(AccountSuspensionService.class);
		Account account = org.mockito.Mockito.mock(Account.class);
		Instant now = Instant.parse("2026-08-15T00:00:00Z");
		given(accounts.findByIdForUpdate(7)).willReturn(Optional.of(account));
		given(warnings.existsByOrderIdAndReason(10, UserWarningReason.BUYER_CANCELLED)).willReturn(false);
		given(warnings.countActiveByUserId(7, now)).willReturn(2L);

		new UserWarningIssuer(accounts, warnings, suspensionService, Clock.fixed(now, ZoneOffset.UTC))
			.issue(7, 10, UserWarningReason.BUYER_CANCELLED);

		verify(warnings).save(org.mockito.Mockito.any(UserWarning.class));
		verify(suspensionService, never()).suspendAutomatically(7);
	}

	@Test
	void 주문_없는_관리자_수동_경고는_최근_중복_발급이_없으면_발급된다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		UserWarningRepository warnings = org.mockito.Mockito.mock(UserWarningRepository.class);
		AccountSuspensionService suspensionService = org.mockito.Mockito.mock(AccountSuspensionService.class);
		Account account = org.mockito.Mockito.mock(Account.class);
		Instant now = Instant.parse("2026-08-15T00:00:00Z");
		given(accounts.findByIdForUpdate(7)).willReturn(Optional.of(account));
		given(warnings.existsByUserIdAndReasonAndIssuedAtAfter(7, UserWarningReason.ADMIN_MANUAL, now.minusSeconds(5)))
			.willReturn(false);
		given(warnings.countActiveByUserId(7, now)).willReturn(1L);

		new UserWarningIssuer(accounts, warnings, suspensionService, Clock.fixed(now, ZoneOffset.UTC))
			.issue(7, null, UserWarningReason.ADMIN_MANUAL);

		verify(warnings, never()).existsByOrderIdAndReason(org.mockito.Mockito.any(), org.mockito.Mockito.any());
		verify(warnings).save(org.mockito.Mockito.any(UserWarning.class));
		verify(suspensionService, never()).suspendAutomatically(7);
	}

	@Test
	void 주문_없는_관리자_수동_경고는_짧은_시간_내_중복_요청이면_건너뛴다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		UserWarningRepository warnings = org.mockito.Mockito.mock(UserWarningRepository.class);
		AccountSuspensionService suspensionService = org.mockito.Mockito.mock(AccountSuspensionService.class);
		Account account = org.mockito.Mockito.mock(Account.class);
		Instant now = Instant.parse("2026-08-15T00:00:00Z");
		given(accounts.findByIdForUpdate(7)).willReturn(Optional.of(account));
		given(warnings.existsByUserIdAndReasonAndIssuedAtAfter(7, UserWarningReason.ADMIN_MANUAL, now.minusSeconds(5)))
			.willReturn(true);

		new UserWarningIssuer(accounts, warnings, suspensionService, Clock.fixed(now, ZoneOffset.UTC))
			.issue(7, null, UserWarningReason.ADMIN_MANUAL);

		verify(warnings, never()).save(org.mockito.Mockito.any());
		verify(suspensionService, never()).suspendAutomatically(7);
	}
}
