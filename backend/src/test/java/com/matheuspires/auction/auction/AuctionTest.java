package com.matheuspires.auction.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.matheuspires.auction.common.error.ApiException;
import com.matheuspires.auction.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuctionTest {

	private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

	private static final UUID SELLER = UUID.randomUUID();

	private final Auction auction = Auction.schedule(UUID.randomUUID(), SELLER, "Item", null, null, 10_000, 500, NOW,
			NOW.plus(Duration.ofHours(1)), NOW);

	@Test
	void startsScheduledAtTheStartingPrice() {
		assertThat(auction.getStatus()).isEqualTo(AuctionStatus.SCHEDULED);
		assertThat(auction.getCurrentPrice()).isEqualTo(10_000);
		assertThat(auction.getBidCount()).isZero();
		assertThat(auction.getLeaderId()).isNull();
		assertThat(auction.isSeller(SELLER)).isTrue();
		assertThat(auction.isSeller(UUID.randomUUID())).isFalse();
	}

	@Test
	void activatesOnlyFromScheduled() {
		auction.activate();

		assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ACTIVE);
		assertThatThrownBy(auction::activate).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cancelsWhileThereAreNoBidsButNotTwice() {
		auction.activate();
		auction.cancel();

		assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
		assertNotCancellable();
	}

	@Test
	void cannotCancelOnceThereAreBids() {
		auction.activate();
		ReflectionTestUtils.setField(auction, "bidCount", 1);

		assertNotCancellable();
	}

	private void assertNotCancellable() {
		assertThatThrownBy(auction::cancel).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.AUCTION_NOT_CANCELLABLE));
	}

}
