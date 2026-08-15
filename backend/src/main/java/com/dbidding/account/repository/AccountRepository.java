package com.dbidding.account.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dbidding.account.domain.Account;

public interface AccountRepository extends JpaRepository<Account, Integer> {

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	Optional<Account> findByEmail(String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT account FROM Account account WHERE account.id = :id")
	Optional<Account> findByIdForUpdate(@Param("id") Integer id);
}
