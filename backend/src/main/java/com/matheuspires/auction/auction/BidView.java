package com.matheuspires.auction.auction;

import java.time.Instant;
import java.util.UUID;

/** A bid as shown to clients. {@code bot} is true for simulated bidders, which the UI labels. */
public record BidView(UUID id, long amount, UUID bidderId, String bidderDisplayName, boolean bot, Instant placedAt) {

}
