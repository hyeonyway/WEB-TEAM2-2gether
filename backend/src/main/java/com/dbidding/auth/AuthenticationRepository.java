package com.dbidding.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthenticationRepository extends JpaRepository<Authentication, Integer> {

	Optional<Authentication> findByUserId(Integer userId);

	Optional<Authentication> findByRefreshTokenHash(String refreshTokenHash);

	void deleteByUserId(Integer userId);
}
