package com.dbidding.wallet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.dbidding.wallet.domain.WalletHold;

import jakarta.persistence.LockModeType;

public interface WalletHoldRepository extends JpaRepository<WalletHold, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<WalletHold> findFirstByWalletIdAndAuctionIdOrderByIdDesc(
		Integer walletId,
		Integer auctionId
	);
}
