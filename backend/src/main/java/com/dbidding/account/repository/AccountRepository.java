package com.dbidding.account.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dbidding.account.domain.Account;

public interface AccountRepository extends JpaRepository<Account, Integer> {

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	Optional<Account> findByEmail(String email);
}
