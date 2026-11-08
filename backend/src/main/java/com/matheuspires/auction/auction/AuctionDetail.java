package com.matheuspires.auction.auction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Auction room snapshot. {@code serverTime} lets clients compute their clock offset, so countdowns
 * follow the server clock instead of the device clock.
 */
public record AuctionDetail(UUID id, String title, String description, String imageUrl, AuctionStatus status,
		long startingPrice, long minIncrement, long currentPrice, long minimumNextBid, int bidCount, Instant startsAt,
		Instant endsAt, UUID sellerId, String sellerDisplayName, UUID leaderId, String leaderDisplayName,
		UUID winnerId, String winnerDisplayName, Instant serverTime, List<BidView> recentBids) {

}
