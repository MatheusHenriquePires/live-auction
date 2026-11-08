package com.matheuspires.auction.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * Conditional revoke: returns 1 only for the caller that flips {@code revoked} from false to true.
	 * Concurrent rotations of the same token are serialized by the row lock, so only one can win.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RefreshToken t set t.revoked = true where t.id = :id and t.revoked = false")
	int revokeIfActive(UUID id);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update RefreshToken t set t.revoked = true where t.userId = :userId and t.revoked = false")
	int revokeAllActiveForUser(UUID userId);

	long countByUserIdAndRevokedFalse(UUID userId);

}
