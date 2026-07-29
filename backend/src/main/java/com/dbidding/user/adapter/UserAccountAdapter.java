package com.dbidding.user.adapter;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.dbidding.auth.exception.DuplicateEmailException;
import com.dbidding.auth.exception.DuplicateNicknameException;
import com.dbidding.auth.port.UserAccount;
import com.dbidding.auth.port.UserAccountPort;
import com.dbidding.auth.port.UserAccountRole;
import com.dbidding.user.domain.User;
import com.dbidding.user.domain.UserRole;
import com.dbidding.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserAccountAdapter implements UserAccountPort {

	private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";
	private static final String NICKNAME_UNIQUE_CONSTRAINT = "uk_users_nickname";

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
		try {
			return toUserAccount(userRepository.saveAndFlush(user));
		} catch (DataIntegrityViolationException exception) {
			if (isConstraintViolation(exception, EMAIL_UNIQUE_CONSTRAINT)) {
				throw new DuplicateEmailException(exception);
			}
			if (isConstraintViolation(exception, NICKNAME_UNIQUE_CONSTRAINT)) {
				throw new DuplicateNicknameException(exception);
			}
			throw exception;
		}
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

	private boolean isConstraintViolation(Throwable exception, String expectedConstraint) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				String constraintName = constraintViolation.getConstraintName();
				if (constraintName == null) {
					return false;
				}
				String normalizedName = constraintName.replace("`", "");
				String unqualifiedName = normalizedName.substring(normalizedName.lastIndexOf('.') + 1);
				return unqualifiedName.equalsIgnoreCase(expectedConstraint);
			}
			cause = cause.getCause();
		}
		return false;
	}
}
