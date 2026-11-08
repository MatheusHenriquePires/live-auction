package com.matheuspires.auction.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.matheuspires.auction.common.Ids;
import com.matheuspires.auction.common.error.ApiException;
import com.matheuspires.auction.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens with rotation and reuse detection (RFC 9700, section 4.14.2).
 * <p>
 * Every refresh revokes the presented token and issues a new one. Presenting a token that is already
 * revoked means it was copied (or replayed by a racing client), so every active token of that user is
 * revoked and the user must log in again.
 */
@Service
public class RefreshTokenService {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

	private static final SecureRandom RANDOM = new SecureRandom();

	private final RefreshTokenRepository tokens;

	private final AuctionSecurityProperties.JwtSettings settings;

	private final Clock clock;

	RefreshTokenService(RefreshTokenRepository tokens, AuctionSecurityProperties properties, Clock clock) {
		this.tokens = tokens;
		this.settings = properties.jwt();
		this.clock = clock;
	}

	@Transactional
	public String issue(UUID userId) {
		String rawToken = newRawToken();
		tokens.save(new RefreshToken(Ids.newId(), userId, hash(rawToken),
				clock.instant().plus(settings.refreshTokenTtl())));
		return rawToken;
	}

	/** Revocations triggered by reuse detection must survive the 401, hence {@code noRollbackFor}. */
	@Transactional(noRollbackFor = ApiException.class)
	public Rotation rotate(String rawToken) {
		RefreshToken token = tokens.findByTokenHash(hash(rawToken)).orElseThrow(RefreshTokenService::invalid);
		if (token.isRevoked()) {
			throw reuseDetected(token.getUserId());
		}
		if (token.isExpired(clock.instant())) {
			throw invalid();
		}
		if (tokens.revokeIfActive(token.getId()) == 0) {
			// Another request rotated this token between our read and our update.
			throw reuseDetected(token.getUserId());
		}
		return new Rotation(token.getUserId(), issue(token.getUserId()));
	}

	@Transactional
	public void revoke(String rawToken) {
		tokens.findByTokenHash(hash(rawToken)).ifPresent(token -> tokens.revokeIfActive(token.getId()));
	}

	public record Rotation(UUID userId, String refreshToken) {
	}

	static String hash(String rawToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every JVM", ex);
		}
	}

	private ApiException reuseDetected(UUID userId) {
		int revoked = tokens.revokeAllActiveForUser(userId);
		log.warn("Refresh token reuse detected for user {}: revoked {} active token(s)", userId, revoked);
		return invalid();
	}

	private static String newRawToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static ApiException invalid() {
		return new ApiException(ErrorCode.UNAUTHORIZED, "Invalid or expired refresh token");
	}

}
