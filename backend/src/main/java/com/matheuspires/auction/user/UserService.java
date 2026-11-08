package com.matheuspires.auction.user;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

import com.matheuspires.auction.common.Ids;
import com.matheuspires.auction.common.error.ApiException;
import com.matheuspires.auction.common.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	/** BCrypt only looks at the first 72 bytes; longer inputs are rejected instead of silently truncated. */
	public static final int MAX_PASSWORD_BYTES = 72;

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	UserService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	@Transactional
	public UserAccount register(String email, String displayName, String rawPassword) {
		return create(email, displayName, rawPassword, Role.USER);
	}

	@Transactional
	public UserAccount create(String email, String displayName, String rawPassword, Role role) {
		if (exceedsBcryptLimit(rawPassword)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "password must be at most 72 bytes");
		}
		String normalizedEmail = normalizeEmail(email);
		if (users.existsByEmail(normalizedEmail)) {
			throw emailTaken();
		}
		UserAccount user = new UserAccount(Ids.newId(), normalizedEmail, displayName.strip(),
				passwordEncoder.encode(rawPassword), role, clock.instant());
		try {
			return users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Lost a race against a concurrent registration with the same e-mail.
			throw emailTaken();
		}
	}

	@Transactional(readOnly = true)
	public UserAccount get(UUID id) {
		return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
	}

	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	public static boolean exceedsBcryptLimit(String rawPassword) {
		return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
	}

	private static ApiException emailTaken() {
		return new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "This e-mail is already registered");
	}

}
