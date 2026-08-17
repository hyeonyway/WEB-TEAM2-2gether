package com.dbidding.account.repository;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountStatus;

public interface AccountRepository extends JpaRepository<Account, Integer> {

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	Optional<Account> findByEmail(String email);

	@Query("SELECT account FROM Account account "
		+ "WHERE account.role <> com.dbidding.account.domain.AccountRole.ADMIN "
		+ "AND (:keyword IS NULL "
		+ "OR LOWER(account.email) LIKE LOWER(CONCAT('%', :keyword, '%')) "
		+ "OR LOWER(account.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) "
		+ "OR account.id = :accountId) "
		+ "AND (:status IS NULL OR account.status = :status) "
		+ "AND (:onlyWarned = FALSE OR EXISTS ("
		+ "  SELECT 1 FROM UserWarning warning "
		+ "  WHERE warning.userId = account.id AND warning.expiresAt > :now"
		+ "))")
	Page<Account> searchForAdmin(
		@Param("keyword") String keyword,
		@Param("accountId") Integer accountId,
		@Param("status") AccountStatus status,
		@Param("onlyWarned") boolean onlyWarned,
		@Param("now") Instant now,
		Pageable pageable
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT account FROM Account account WHERE account.id = :id")
	Optional<Account> findByIdForUpdate(@Param("id") Integer id);
}
