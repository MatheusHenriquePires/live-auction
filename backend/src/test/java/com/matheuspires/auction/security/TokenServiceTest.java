package com.matheuspires.auction.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.matheuspires.auction.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class TokenServiceTest {

	private static final String SECRET = "unit-test-secret-0123456789abcdefghijkl";

	private static final String ISSUER = "test-issuer";

	private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

	@Test
	void accessTokenCarriesIdentityAndExpiresAfterTheConfiguredTtl() {
		UUID userId = UUID.randomUUID();

		Jwt jwt = JwtCodecs.decoder(SECRET, ISSUER)
			.decode(service(SECRET, ISSUER).issueAccessToken(userId, "ana@example.com", Role.ADMIN));

		assertThat(jwt.getSubject()).isEqualTo(userId.toString());
		assertThat(jwt.getClaimAsString(TokenService.ROLE_CLAIM)).isEqualTo("ADMIN");
		assertThat(jwt.getClaimAsString(TokenService.EMAIL_CLAIM)).isEqualTo("ana@example.com");
		assertThat(jwt.getIssuedAt()).isEqualTo(now);
		assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
		assertThat(service(SECRET, ISSUER).accessTokenTtlSeconds()).isEqualTo(900);
	}

	@Test
	void tokenSignedWithAnotherSecretIsRejected() {
		String token = service("another-secret-0123456789abcdefghijklmn", ISSUER)
			.issueAccessToken(UUID.randomUUID(), "x@example.com", Role.USER);

		assertThatThrownBy(() -> JwtCodecs.decoder(SECRET, ISSUER).decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void tokenFromAnotherIssuerIsRejected() {
		String token = service(SECRET, "someone-else").issueAccessToken(UUID.randomUUID(), "x@example.com", Role.USER);

		assertThatThrownBy(() -> JwtCodecs.decoder(SECRET, ISSUER).decode(token)).isInstanceOf(JwtException.class);
	}

	private TokenService service(String secret, String issuer) {
		var properties = new AuctionSecurityProperties(
				new AuctionSecurityProperties.JwtSettings(secret, issuer, Duration.ofMinutes(15), Duration.ofDays(7)), 4,
				List.of("http://localhost:5173"));
		return new TokenService(JwtCodecs.encoder(secret), properties, Clock.fixed(now, ZoneOffset.UTC));
	}

}
