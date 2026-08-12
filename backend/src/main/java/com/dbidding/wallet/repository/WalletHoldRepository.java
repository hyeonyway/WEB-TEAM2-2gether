package com.dbidding.wallet.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dbidding.wallet.domain.WalletHold;
public interface WalletHoldRepository extends JpaRepository<WalletHold, Long> {

	/**
	 * 별도 락을 걸지 않는다 — 호출자(WalletService)가 항상 지갑 행(FOR UPDATE)을
	 * 먼저 잠근 뒤에만 이 메서드를 부르므로, 같은 지갑의 홀드 변경은 지갑 행 락
	 * 하나로 이미 직렬화된다. wallet_holds에 별도 락을 얹으면 지갑 행 락과 함께
	 * 잠금 자원이 2개가 되어, 서로 다른 독립 거래에 낀 3개 이상 지갑이 얽힐 때
	 * 데드락이 생길 수 있다(#393).
	 */
	Optional<WalletHold> findFirstByWalletIdAndAuctionIdOrderByIdDesc(
		Integer walletId,
		Integer auctionId
	);

	Optional<WalletHold> findTopByWalletIdAndAuctionIdOrderByIdDesc(Integer walletId, Integer auctionId);

	boolean existsByEventId(UUID eventId);

	@Query(value = """
		SELECT w.user_id AS userId, wh.auction_id AS auctionId, wh.amount AS amount
		FROM wallet_holds wh JOIN wallets w ON w.id = wh.wallet_id
		WHERE w.user_id IN (:userIds) AND wh.status = 'HELD'
		""", nativeQuery = true)
	List<WalletHeldHoldRow> findHeldRowsForUsers(@Param("userIds") Collection<Integer> userIds);
}
