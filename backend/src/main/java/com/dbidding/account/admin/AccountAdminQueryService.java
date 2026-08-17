package com.dbidding.account.admin;

import com.dbidding.account.admin.dto.AdminAccountPageResponse;
import com.dbidding.account.admin.dto.AdminAccountResponse;
import com.dbidding.account.admin.dto.UserWarningResponse;
import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.exception.AccountNotFoundException;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.warning.UserWarningRepository;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAdminQueryService {

	private final AccountRepository accountRepository;
	private final UserWarningRepository userWarningRepository;
	private final Supplier<Instant> nowSupplier;

	@Autowired
	public AccountAdminQueryService(AccountRepository accountRepository, UserWarningRepository userWarningRepository) {
		this(accountRepository, userWarningRepository, Instant::now);
	}

	AccountAdminQueryService(
		AccountRepository accountRepository,
		UserWarningRepository userWarningRepository,
		Supplier<Instant> nowSupplier
	) {
		this.accountRepository = accountRepository;
		this.userWarningRepository = userWarningRepository;
		this.nowSupplier = nowSupplier;
	}

	@Transactional(readOnly = true)
	public AdminAccountPageResponse findAccounts(
		Integer actorId, int page, int size, String keyword, AccountStatus status, boolean onlyWarned
	) {
		requireAdmin(actorId);
		String normalizedKeyword = normalizeKeyword(keyword);
		Integer accountId = toAccountId(normalizedKeyword);
		Instant now = nowSupplier.get();
		Page<Account> accounts = accountRepository.searchForAdmin(
			normalizedKeyword, accountId, status, onlyWarned, now, PageRequest.of(page, size)
		);
		List<AdminAccountResponse> content = accounts.getContent().stream()
			.map(account -> toResponse(account, now))
			.toList();
		return new AdminAccountPageResponse(content, accounts.getNumber(), accounts.getSize(),
			accounts.getTotalElements(), accounts.getTotalPages());
	}

	@Transactional(readOnly = true)
	public List<UserWarningResponse> findWarnings(Integer actorId, Integer targetId) {
		requireAdmin(actorId);
		if (!accountRepository.existsById(targetId)) {
			throw new AccountNotFoundException();
		}
		return userWarningRepository.findByUserIdOrderByIssuedAtDescIdDesc(targetId).stream()
			.map(UserWarningResponse::from)
			.toList();
	}

	private AdminAccountResponse toResponse(Account account, Instant now) {
		long activeWarningCount = userWarningRepository.countActiveByUserId(account.getId(), now);
		Instant latestActiveWarningExpiresAt = userWarningRepository
			.findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(account.getId(), now)
			.map(warning -> warning.getExpiresAt())
			.orElse(null);
		return new AdminAccountResponse(account.getId(), account.getEmail(), account.getNickname(), account.getRole(),
			account.getStatus(), account.getCreatedAt(), activeWarningCount, latestActiveWarningExpiresAt);
	}

	private void requireAdmin(Integer actorId) {
		boolean admin = accountRepository.findById(actorId)
			.map(account -> account.getRole() == AccountRole.ADMIN)
			.orElse(false);
		if (!admin) {
			throw new AccountAdminAccessDeniedException();
		}
	}

	private String normalizeKeyword(String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return null;
		}
		return keyword.trim();
	}

	private Integer toAccountId(String keyword) {
		if (keyword == null) {
			return null;
		}
		try {
			return Integer.valueOf(keyword);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
}
