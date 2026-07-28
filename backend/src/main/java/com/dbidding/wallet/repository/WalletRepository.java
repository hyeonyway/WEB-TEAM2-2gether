package com.dbidding.wallet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dbidding.wallet.domain.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, Integer> {

	Optional<Wallet> findByUserId(Integer userId);

	boolean existsByUserId(Integer userId);
}
