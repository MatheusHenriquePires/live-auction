package com.matheuspires.auction.security;

import java.time.Instant;
import java.util.UUID;

import com.matheuspires.auction.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A refresh token. Only its SHA-256 hash is stored, so a database leak does not leak usable tokens. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AssignedIdEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "token_hash", nullable = false, unique = true, length = 100)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private boolean revoked;

	protected RefreshToken() {
	}

	RefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
		super(id);
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public UUID getUserId() {
		return userId;
	}

	public boolean isRevoked() {
		return revoked;
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

}
