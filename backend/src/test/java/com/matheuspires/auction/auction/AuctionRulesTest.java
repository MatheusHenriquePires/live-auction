package com.matheuspires.auction.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import com.matheuspires.auction.common.error.ApiException;
import com.matheuspires.auction.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class AuctionRulesTest {

	private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

	@Test
	void acceptsTheShortestAuctionStartingNow() {
		assertThatNoException()
			.isThrownBy(() -> AuctionRules.validateSchedule(NOW, NOW, NOW.plus(AuctionRules.MIN_DURATION)));
	}

	@Test
	void toleratesClockSkewOnTheStartButNotMore() {
		assertThatNoException()
			.isThrownBy(() -> AuctionRules.validateSchedule(NOW, NOW.minusSeconds(60), NOW.plus(Duration.ofHours(1))));
		assertRejected(NOW.minusSeconds(61), NOW.plus(Duration.ofHours(1)));
	}

	@Test
	void rejectsAuctionsShorterThanOneMinute() {
		assertRejected(NOW, NOW.plusSeconds(59));
		assertRejected(NOW, NOW.minusSeconds(1));
	}

	@Test
	void rejectsAuctionsLongerThanThirtyDays() {
		assertThatNoException().isThrownBy(() -> AuctionRules.validateSchedule(NOW, NOW, NOW.plus(Duration.ofDays(30))));
		assertRejected(NOW, NOW.plus(Duration.ofDays(30)).plusSeconds(1));
	}

	@Test
	void rejectsStartsTooFarAhead() {
		Instant startsAt = NOW.plus(Duration.ofDays(30)).plusSeconds(1);
		assertRejected(startsAt, startsAt.plus(Duration.ofHours(1)));
	}

	@Test
	void minimumNextBidIsTheStartingPriceUntilTheFirstBid() {
		assertThat(AuctionRules.minimumNextBid(10_000, 10_000, 500, 0)).isEqualTo(10_000);
		assertThat(AuctionRules.minimumNextBid(10_000, 12_000, 500, 3)).isEqualTo(12_500);
	}

	private static void assertRejected(Instant startsAt, Instant endsAt) {
		assertThatThrownBy(() -> AuctionRules.validateSchedule(NOW, startsAt, endsAt))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

}
