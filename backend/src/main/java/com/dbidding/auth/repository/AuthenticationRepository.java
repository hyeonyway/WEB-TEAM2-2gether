package com.dbidding.auth.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dbidding.auth.domain.Authentication;

public interface AuthenticationRepository extends JpaRepository<Authentication, Integer> {

	Optional<Authentication> findByUserId(Integer userId);

	Optional<Authentication> findByRefreshTokenHash(String refreshTokenHash);

	void deleteByUserId(Integer userId);
}
