package com.matheuspires.auction.auction;

import java.time.Duration;
import java.time.Instant;

import com.matheuspires.auction.common.error.ApiException;
import com.matheuspires.auction.common.error.ErrorCode;

/** Pure domain rules (no I/O), unit-tested in isolation. */
public final class AuctionRules {

	/** Clients may send a start time slightly in the past because of clock skew. */
	public static final Duration START_SKEW_TOLERANCE = Duration.ofMinutes(1);

	public static final Duration MIN_DURATION = Duration.ofMinutes(1);

	public static final Duration MAX_DURATION = Duration.ofDays(30);

	public static final Duration MAX_SCHEDULE_AHEAD = Duration.ofDays(30);

	private AuctionRules() {
	}

	public static void validateSchedule(Instant now, Instant startsAt, Instant endsAt) {
		if (startsAt.isBefore(now.minus(START_SKEW_TOLERANCE))) {
			throw invalid("startsAt must not be in the past");
		}
		if (startsAt.isAfter(now.plus(MAX_SCHEDULE_AHEAD))) {
			throw invalid("startsAt must be at most 30 days ahead");
		}
		Duration duration = Duration.between(startsAt, endsAt);
		if (duration.compareTo(MIN_DURATION) < 0) {
			throw invalid("endsAt must be at least 1 minute after startsAt");
		}
		if (duration.compareTo(MAX_DURATION) > 0) {
			throw invalid("an auction can last at most 30 days");
		}
	}

	/** First bid must meet the starting price; later bids must beat the current price by the increment. */
	public static long minimumNextBid(long startingPrice, long currentPrice, long minIncrement, int bidCount) {
		return bidCount == 0 ? startingPrice : currentPrice + minIncrement;
	}

	private static ApiException invalid(String detail) {
		return new ApiException(ErrorCode.VALIDATION_ERROR, detail);
	}

}
