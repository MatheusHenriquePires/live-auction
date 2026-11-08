package com.matheuspires.auction.security;

import java.util.UUID;

import com.matheuspires.auction.user.Role;
import org.springframework.security.oauth2.jwt.Jwt;

/** Identity extracted from a validated access token. */
public record AuthenticatedUser(UUID id, Role role) {

	public static AuthenticatedUser from(Jwt jwt) {
		return new AuthenticatedUser(UUID.fromString(jwt.getSubject()),
				Role.valueOf(jwt.getClaimAsString(TokenService.ROLE_CLAIM)));
	}

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}

}
