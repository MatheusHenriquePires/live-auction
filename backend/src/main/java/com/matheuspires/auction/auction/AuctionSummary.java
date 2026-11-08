package com.matheuspires.auction.auction;

import java.time.Instant;
import java.util.UUID;

/** Lobby card. Amounts in cents, times in UTC. */
public record AuctionSummary(UUID id, String title, String imageUrl, AuctionStatus status, long currentPrice,
		long minimumNextBid, int bidCount, Instant startsAt, Instant endsAt, UUID sellerId, String sellerDisplayName,
		UUID leaderId, String leaderDisplayName) {

}
