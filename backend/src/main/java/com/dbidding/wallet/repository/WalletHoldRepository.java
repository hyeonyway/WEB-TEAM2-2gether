package com.dbidding.wallet.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dbidding.wallet.domain.WalletHold;

public interface WalletHoldRepository extends JpaRepository<WalletHold, Long> {

	Optional<WalletHold> findFirstByWalletIdAndAuctionIdOrderByIdDesc(
		Integer walletId,
		Integer auctionId
	);
}
