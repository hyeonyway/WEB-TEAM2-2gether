package com.dbidding.account.warning;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserWarningRepository extends JpaRepository<UserWarning, Long> {

	boolean existsByOrderIdAndReason(Integer orderId, UserWarningReason reason);

	boolean existsByUserIdAndReasonAndIssuedAtAfter(Integer userId, UserWarningReason reason, Instant after);

	@Query("SELECT COUNT(userWarning) FROM UserWarning userWarning "
		+ "WHERE userWarning.userId = :userId AND userWarning.expiresAt > :now")
	long countActiveByUserId(@Param("userId") Integer userId, @Param("now") Instant now);

	Optional<UserWarning> findFirstByUserIdAndExpiresAtAfterOrderByExpiresAtDesc(Integer userId, Instant now);

	List<UserWarning> findByUserIdOrderByIssuedAtDescIdDesc(Integer userId);

	@Query("SELECT userWarning.userId AS userId, COUNT(userWarning) AS activeCount, "
		+ "MAX(userWarning.expiresAt) AS latestExpiresAt "
		+ "FROM UserWarning userWarning "
		+ "WHERE userWarning.userId IN :userIds AND userWarning.expiresAt > :now "
		+ "GROUP BY userWarning.userId")
	List<ActiveWarningStats> findActiveWarningStats(@Param("userIds") Collection<Integer> userIds, @Param("now") Instant now);

	interface ActiveWarningStats {
		Integer getUserId();

		long getActiveCount();

		Instant getLatestExpiresAt();
	}
}
