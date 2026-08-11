package com.dbidding.wallet.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dbidding.wallet.domain.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, Integer> {

	Optional<Wallet> findByUserId(Integer userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT wallet FROM Wallet wallet WHERE wallet.userId = :userId")
	Optional<Wallet> findByUserIdForUpdate(@Param("userId") Integer userId);

	@Query(value = """
		SELECT COALESCE(SUM(wallet_hold.amount), 0)
		FROM wallet_holds wallet_hold
		WHERE wallet_hold.wallet_id = :walletId
		  AND wallet_hold.status = 'HELD'
		""", nativeQuery = true)
	long sumHeldAmount(@Param("walletId") Integer walletId);

	/**
	 * REPEATABLE READ의 읽기 뷰와 무관하게 최신 예치금을 읽어야 하는 쓰기 경로용 current read다.
	 * 호출자는 반드시 해당 wallet 행을 먼저 잠가 같은 지갑의 hold 변경을 직렬화해야 한다.
	 */
	@Query(value = """
		SELECT COALESCE(SUM(wallet_hold.amount), 0)
		FROM wallet_holds wallet_hold
		WHERE wallet_hold.wallet_id = :walletId
		  AND wallet_hold.status = 'HELD'
		FOR UPDATE
		""", nativeQuery = true)
	long sumHeldAmountForUpdate(@Param("walletId") Integer walletId);

	boolean existsByUserId(Integer userId);
}
