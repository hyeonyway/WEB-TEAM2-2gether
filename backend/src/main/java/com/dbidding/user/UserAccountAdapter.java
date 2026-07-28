package com.dbidding.user;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.dbidding.auth.port.UserAccount;
import com.dbidding.auth.port.UserAccountPort;
import com.dbidding.auth.port.UserAccountRole;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserAccountAdapter implements UserAccountPort {

	private final UserRepository userRepository;

	@Override
	public boolean existsByEmail(String email) {
		return userRepository.existsByEmail(email);
	}

	@Override
	public boolean existsByNickname(String nickname) {
		return userRepository.existsByNickname(nickname);
	}

	@Override
	public UserAccount create(
		String email,
		String nickname,
		String encryptedPassword,
		String salt
	) {
		User user = User.create(email, nickname, encryptedPassword, salt);
		return toUserAccount(userRepository.save(user));
	}

	@Override
	public Optional<UserAccount> findByEmail(String email) {
		return userRepository.findByEmail(email).map(this::toUserAccount);
	}

	@Override
	public Optional<UserAccount> findById(Integer userId) {
		return userRepository.findById(userId).map(this::toUserAccount);
	}

	private UserAccount toUserAccount(User user) {
		return new UserAccount(
			user.getId(),
			user.getEmail(),
			user.getNickname(),
			toUserAccountRole(user.getRole()),
			user.getStatus().name(),
			user.getEncryptedPassword(),
			user.getSalt()
		);
	}

	private UserAccountRole toUserAccountRole(UserRole role) {
		return switch (role) {
			case USER -> UserAccountRole.USER;
			case ADMIN -> UserAccountRole.ADMIN;
		};
	}
}
