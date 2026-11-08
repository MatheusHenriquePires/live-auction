package com.matheuspires.auction.auction;

/** Lifecycle: SCHEDULED -> ACTIVE -> ENDED, or CANCELLED while there are no bids. */
public enum AuctionStatus {

	SCHEDULED, ACTIVE, ENDED, CANCELLED

}
