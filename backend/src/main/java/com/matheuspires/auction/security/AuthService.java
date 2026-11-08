package com.matheuspires.auction.security;

import java.util.Optional;

import com.matheuspires.auction.common.error.ApiException;
import com.matheuspires.auction.common.error.ErrorCode;
import com.matheuspires.auction.user.UserAccount;
import com.matheuspires.auction.user.UserRepository;
import com.matheuspires.auction.user.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokenService;

	private final RefreshTokenService refreshTokens;

	/** Hash compared against when the e-mail is unknown, so both failure paths take the same time. */
	private final String timingEqualizerHash;

	AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService,
			RefreshTokenService refreshTokens) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.refreshTokens = refreshTokens;
		this.timingEqualizerHash = passwordEncoder.encode("timing-equalizer-password");
	}

	public TokenResponse login(String email, String password) {
		if (UserService.exceedsBcryptLimit(password)) {
			throw invalidCredentials();
		}
		Optional<UserAccount> user = users.findByEmail(UserService.normalizeEmail(email));
		if (user.isEmpty()) {
			passwordEncoder.matches(password, timingEqualizerHash);
			throw invalidCredentials();
		}
		if (!passwordEncoder.matches(password, user.get().getPasswordHash())) {
			throw invalidCredentials();
		}
		return tokensFor(user.get(), refreshTokens.issue(user.get().getId()));
	}

	public TokenResponse refresh(String refreshToken) {
		RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken);
		UserAccount user = users.findById(rotation.userId())
			.orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid or expired refresh token"));
		return tokensFor(user, rotation.refreshToken());
	}

	public void logout(String refreshToken) {
		refreshTokens.revoke(refreshToken);
	}

	private TokenResponse tokensFor(UserAccount user, String refreshToken) {
		String accessToken = tokenService.issueAccessToken(user.getId(), user.getEmail(), user.getRole());
		return new TokenResponse(accessToken, refreshToken, tokenService.accessTokenTtlSeconds());
	}

	private static ApiException invalidCredentials() {
		return new ApiException(ErrorCode.UNAUTHORIZED, "Invalid e-mail or password");
	}

}
