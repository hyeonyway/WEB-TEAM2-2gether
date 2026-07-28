package com.dbidding.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dbidding.auth.domain.Authentication;

public interface AuthenticationRepository extends JpaRepository<Authentication, Integer> {

	Optional<Authentication> findByUserId(Integer userId);

	Optional<Authentication> findByRefreshTokenHash(String refreshTokenHash);

	@Modifying
	@Query(value = """
		INSERT INTO authentication (user_id, refresh_token)
		VALUES (:userId, :refreshTokenHash)
		ON DUPLICATE KEY UPDATE refresh_token = :refreshTokenHash
		""", nativeQuery = true)
	int upsertRefreshTokenHash(
		@Param("userId") Integer userId,
		@Param("refreshTokenHash") String refreshTokenHash
	);

	void deleteByUserId(Integer userId);
}
