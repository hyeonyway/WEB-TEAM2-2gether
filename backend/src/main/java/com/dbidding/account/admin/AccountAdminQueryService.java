package com.dbidding.account.admin;

import com.dbidding.account.admin.dto.AdminAccountPageResponse;
import com.dbidding.account.admin.dto.AdminAccountResponse;
import com.dbidding.account.admin.dto.UserWarningResponse;
import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.exception.AccountNotFoundException;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.warning.UserWarningIssuer;
import com.dbidding.account.warning.UserWarningRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAdminQueryService {

	private final AccountRepository accountRepository;
	private final UserWarningRepository userWarningRepository;
	private final AccountAdminAuthorization authorization;
	private final Supplier<Instant> nowSupplier;

	@Autowired
	public AccountAdminQueryService(
		AccountRepository accountRepository,
		UserWarningRepository userWarningRepository,
		AccountAdminAuthorization authorization
	) {
		this(accountRepository, userWarningRepository, authorization, Instant::now);
	}

	AccountAdminQueryService(
		AccountRepository accountRepository,
		UserWarningRepository userWarningRepository,
		AccountAdminAuthorization authorization,
		Supplier<Instant> nowSupplier
	) {
		this.accountRepository = accountRepository;
		this.userWarningRepository = userWarningRepository;
		this.authorization = authorization;
		this.nowSupplier = nowSupplier;
	}

	@Transactional(readOnly = true)
	public AdminAccountPageResponse findAccounts(
		Integer actorId, int page, int size, String keyword, AccountStatus status, boolean onlyWarned
	) {
		authorization.requireAdmin(actorId);
		String normalizedKeyword = normalizeKeyword(keyword);
		Integer accountId = toAccountId(normalizedKeyword);
		Instant now = nowSupplier.get();
		Page<Account> accounts = accountRepository.searchForAdmin(
			normalizedKeyword, accountId, status, onlyWarned, now, PageRequest.of(page, size)
		);
		List<Integer> accountIds = accounts.getContent().stream().map(Account::getId).toList();
		Map<Integer, UserWarningRepository.ActiveWarningStats> statsByUserId = accountIds.isEmpty()
			? Map.of()
			: userWarningRepository.findActiveWarningStats(accountIds, now).stream()
				.collect(Collectors.toMap(UserWarningRepository.ActiveWarningStats::getUserId, stats -> stats));
		List<AdminAccountResponse> content = accounts.getContent().stream()
			.map(account -> toResponse(account, statsByUserId.get(account.getId())))
			.toList();
		return new AdminAccountPageResponse(content, accounts.getNumber(), accounts.getSize(),
			accounts.getTotalElements(), accounts.getTotalPages(), UserWarningIssuer.SUSPENSION_WARNING_COUNT);
	}

	@Transactional(readOnly = true)
	public List<UserWarningResponse> findWarnings(Integer actorId, Integer targetId) {
		authorization.requireAdmin(actorId);
		if (!accountRepository.existsById(targetId)) {
			throw new AccountNotFoundException();
		}
		return userWarningRepository.findByUserIdOrderByIssuedAtDescIdDesc(targetId).stream()
			.map(UserWarningResponse::from)
			.toList();
	}

	private AdminAccountResponse toResponse(Account account, UserWarningRepository.ActiveWarningStats stats) {
		long activeWarningCount = stats == null ? 0 : stats.getActiveCount();
		Instant latestActiveWarningExpiresAt = stats == null ? null : stats.getLatestExpiresAt();
		return new AdminAccountResponse(account.getId(), account.getEmail(), account.getNickname(), account.getRole(),
			account.getStatus(), account.getCreatedAt(), activeWarningCount, latestActiveWarningExpiresAt);
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
