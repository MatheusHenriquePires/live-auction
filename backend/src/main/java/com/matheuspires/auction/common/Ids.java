package com.matheuspires.auction.common;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates time-ordered UUIDv7 identifiers (RFC 9562).
 * <p>
 * The first 48 bits are the Unix timestamp in milliseconds, so new rows land at the end of B-tree
 * indexes instead of at random positions (as UUIDv4 would), which keeps inserts cheap.
 */
public final class Ids {

	private static final SecureRandom RANDOM = new SecureRandom();

	private Ids() {
	}

	public static UUID newId() {
		long timestamp = System.currentTimeMillis();
		long mostSigBits = (timestamp << 16) | 0x7000L | (RANDOM.nextInt() & 0x0FFFL);
		long leastSigBits = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
		return new UUID(mostSigBits, leastSigBits);
	}

}
