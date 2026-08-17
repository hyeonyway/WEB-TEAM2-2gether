package com.dbidding.account.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.warning.UserWarning;
import com.dbidding.account.warning.UserWarningReason;
import com.dbidding.account.warning.UserWarningRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class AccountAdminQueryServiceTest {

	@Test
	void 관리자가_회원_목록에서_활성_경고_요약을_조회한다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		UserWarningRepository warnings = org.mockito.Mockito.mock(UserWarningRepository.class);
		Account admin = account(1, AccountRole.ADMIN, AccountStatus.ACTIVE, "admin@example.com", "관리자");
		Account user = account(2, AccountRole.USER, AccountStatus.SUSPENDED, "pikachu@example.com", "피카츄");
		Instant now = Instant.parse("2026-08-15T00:00:00Z");
		Instant expiresAt = Instant.parse("2026-09-01T00:00:00Z");
		given(accounts.findById(1)).willReturn(Optional.of(admin));
		given(accounts.searchForAdmin("피카츄", null, null, false, now, PageRequest.of(0, 20)))
			.willReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));
		given(warnings.countActiveByUserId(2, now)).willReturn(1L);
		given(warnings.findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(2, now))
			.willReturn(Optional.of(UserWarning.issued(2, 100, UserWarningReason.BUYER_CANCELLED, now, expiresAt)));

		var response = new AccountAdminQueryService(accounts, warnings, () -> now)
			.findAccounts(1, 0, 20, "피카츄", null, false);

		assertThat(response.content()).singleElement().satisfies(account -> {
			assertThat(account.status()).isEqualTo(AccountStatus.SUSPENDED);
			assertThat(account.activeWarningCount()).isEqualTo(1);
			assertThat(account.latestActiveWarningExpiresAt()).isEqualTo(expiresAt);
		});
		verify(accounts).searchForAdmin("피카츄", null, null, false, now, PageRequest.of(0, 20));
	}

	@Test
	void 관리자가_대상의_경고_이력을_발급일_내림차순으로_조회한다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		UserWarningRepository warnings = org.mockito.Mockito.mock(UserWarningRepository.class);
		Account admin = account(1, AccountRole.ADMIN, AccountStatus.ACTIVE, "admin@example.com", "관리자");
		Account user = account(2, AccountRole.USER, AccountStatus.ACTIVE, "user@example.com", "사용자");
		given(accounts.findById(1)).willReturn(Optional.of(admin));
		given(accounts.existsById(2)).willReturn(true);
		given(warnings.findByUserIdOrderByIssuedAtDescIdDesc(2)).willReturn(List.of(
			UserWarning.issued(2, 101, UserWarningReason.SELLER_CANCELLED,
				Instant.parse("2026-08-10T00:00:00Z"), Instant.parse("2026-09-09T00:00:00Z")),
			UserWarning.issued(2, 100, UserWarningReason.BUYER_CANCELLED,
				Instant.parse("2026-08-09T00:00:00Z"), Instant.parse("2026-09-08T00:00:00Z"))
		));

		var response = new AccountAdminQueryService(accounts, warnings, Instant::now).findWarnings(1, 2);

		assertThat(response).extracting(warning -> warning.orderId()).containsExactly(101, 100);
	}

	@Test
	void 일반_사용자는_관리자_목록을_조회할_수_없다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		UserWarningRepository warnings = org.mockito.Mockito.mock(UserWarningRepository.class);
		Account user = account(1, AccountRole.USER, AccountStatus.ACTIVE, "user@example.com", "사용자");
		given(accounts.findById(1)).willReturn(Optional.of(user));

		assertThatThrownBy(() -> new AccountAdminQueryService(accounts, warnings, Instant::now)
			.findAccounts(1, 0, 20, null, null, false))
			.isInstanceOf(AccountAdminAccessDeniedException.class);
	}

	@Test
	void 상태와_경고_필터를_리포지토리_조회에_그대로_전달한다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		UserWarningRepository warnings = org.mockito.Mockito.mock(UserWarningRepository.class);
		Account admin = account(1, AccountRole.ADMIN, AccountStatus.ACTIVE, "admin@example.com", "관리자");
		Instant now = Instant.parse("2026-08-15T00:00:00Z");
		given(accounts.findById(1)).willReturn(Optional.of(admin));
		given(accounts.searchForAdmin(null, null, AccountStatus.SUSPENDED, true, now, PageRequest.of(0, 20)))
			.willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		new AccountAdminQueryService(accounts, warnings, () -> now)
			.findAccounts(1, 0, 20, null, AccountStatus.SUSPENDED, true);

		verify(accounts).searchForAdmin(null, null, AccountStatus.SUSPENDED, true, now, PageRequest.of(0, 20));
	}

	private Account account(Integer id, AccountRole role, AccountStatus status, String email, String nickname) {
		Account account = org.mockito.Mockito.mock(Account.class);
		given(account.getId()).willReturn(id);
		given(account.getRole()).willReturn(role);
		given(account.getStatus()).willReturn(status);
		given(account.getEmail()).willReturn(email);
		given(account.getNickname()).willReturn(nickname);
		given(account.getCreatedAt()).willReturn(Instant.parse("2026-08-01T00:00:00Z"));
		return account;
	}
}
