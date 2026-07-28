package com.dbidding.auth.port;

import java.util.Optional;

public interface UserAccountPort {

	boolean existsByEmail(String email);

	boolean existsByNickname(String nickname);

	UserAccount create(String email, String nickname, String encryptedPassword, String salt);

	Optional<UserAccount> findByEmail(String email);

	Optional<UserAccount> findById(Integer userId);
}
