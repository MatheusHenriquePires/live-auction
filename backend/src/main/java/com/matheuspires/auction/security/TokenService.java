package com.matheuspires.auction.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.matheuspires.auction.user.Role;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived HS256 access tokens with claims {@code sub}, {@code email} and {@code role}. */
@Service
public class TokenService {

	public static final String ROLE_CLAIM = "role";

	public static final String EMAIL_CLAIM = "email";

	private final JwtEncoder encoder;

	private final AuctionSecurityProperties.JwtSettings settings;

	private final Clock clock;

	public TokenService(JwtEncoder encoder, AuctionSecurityProperties properties, Clock clock) {
		this.encoder = encoder;
		this.settings = properties.jwt();
		this.clock = clock;
	}

	public String issueAccessToken(UUID userId, String email, Role role) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(settings.issuer())
			.subject(userId.toString())
			.issuedAt(now)
			.expiresAt(now.plus(settings.accessTokenTtl()))
			.claim(EMAIL_CLAIM, email)
			.claim(ROLE_CLAIM, role.name())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public long accessTokenTtlSeconds() {
		return settings.accessTokenTtl().toSeconds();
	}

}
