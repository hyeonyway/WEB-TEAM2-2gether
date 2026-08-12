package com.dbidding.wallet.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.dbidding.wallet.domain.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, Integer> {

	Optional<Wallet> findByUserId(Integer userId);

	@Query(value = """
		SELECT w.user_id AS userId, w.point AS point, w.projection_version AS projectionVersion,
		       COALESCE(SUM(CASE WHEN wh.status = 'HELD' THEN wh.amount ELSE 0 END), 0) AS frozenBalance
		FROM wallets w LEFT JOIN wallet_holds wh ON wh.wallet_id = w.id
		GROUP BY w.id, w.user_id, w.point, w.projection_version
		ORDER BY w.id
		""", countQuery = "SELECT COUNT(*) FROM wallets", nativeQuery = true)
	Page<WalletBootstrapRow> findBootstrapRows(Pageable pageable);

	@Modifying
	@Query(value = "UPDATE wallets SET point = :point, projection_version = :version WHERE user_id = :userId AND projection_version < :version", nativeQuery = true)
	int updateProjectionIfNewer(@Param("userId") Integer userId, @Param("point") long point, @Param("version") long version);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT wallet FROM Wallet wallet WHERE wallet.userId = :userId")
	Optional<Wallet> findByUserIdForUpdate(@Param("userId") Integer userId);

	/**
	 * 호출자는 반드시 해당 wallet 행을 먼저 잠가(findByUserIdForUpdate) 같은 지갑의
	 * hold 변경을 직렬화해야 한다. 예전에는 REPEATABLE READ의 읽기 뷰를 우회하려고
	 * 이 쿼리에도 별도로 FOR UPDATE를 걸었는데(sumHeldAmountForUpdate), 지갑 행
	 * 락이 이미 wallet_holds 변경 전체를 배타적으로 보호하므로 중복이었고 오히려
	 * wallet_holds를 별개의 잠금 자원으로 만들어 데드락을 유발했다(#393).
	 */
	@Query(value = """
		SELECT COALESCE(SUM(wallet_hold.amount), 0)
		FROM wallet_holds wallet_hold
		WHERE wallet_hold.wallet_id = :walletId
		  AND wallet_hold.status = 'HELD'
		""", nativeQuery = true)
	long sumHeldAmount(@Param("walletId") Integer walletId);

	boolean existsByUserId(Integer userId);
}
