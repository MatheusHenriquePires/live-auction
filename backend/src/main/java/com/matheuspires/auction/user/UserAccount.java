package com.matheuspires.auction.user;

import java.time.Instant;
import java.util.UUID;

import com.matheuspires.auction.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A registered account. Named {@code UserAccount} to avoid clashing with Spring Security's {@code User}. */
@Entity
@Table(name = "users")
public class UserAccount extends AssignedIdEntity {

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(name = "display_name", nullable = false, length = 60)
	private String displayName;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected UserAccount() {
	}

	public UserAccount(UUID id, String email, String displayName, String passwordHash, Role role, Instant createdAt) {
		super(id);
		this.email = email;
		this.displayName = displayName;
		this.passwordHash = passwordHash;
		this.role = role;
		this.createdAt = createdAt;
	}

	public String getEmail() {
		return email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
