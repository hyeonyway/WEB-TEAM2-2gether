package com.dbidding.account.warning;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserWarningRepository extends JpaRepository<UserWarning, Long> {

	boolean existsByOrderIdAndReason(Integer orderId, UserWarningReason reason);

	@Query("SELECT COUNT(userWarning) FROM UserWarning userWarning "
		+ "WHERE userWarning.userId = :userId AND userWarning.expiresAt > :now")
	long countActiveByUserId(@Param("userId") Integer userId, @Param("now") Instant now);
}
