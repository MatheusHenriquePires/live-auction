package com.matheuspires.auction.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matheuspires.auction.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuctionSortTest {

	@Test
	void defaultsToAscending() {
		assertThat(AuctionSort.parse("endsAt").toSql()).isEqualTo("a.ends_at ASC");
	}

	@Test
	void acceptsAnExplicitDirection() {
		assertThat(AuctionSort.parse("currentPrice,desc").toSql()).isEqualTo("a.current_price DESC");
		assertThat(AuctionSort.parse(" bidCount , ASC ").toSql()).isEqualTo("a.bid_count ASC");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "title", "endsAt,sideways", "endsAt,asc,extra", "a.ends_at; DROP TABLE users" })
	void rejectsEverythingOutsideTheWhitelist(String raw) {
		assertThatThrownBy(() -> AuctionSort.parse(raw)).isInstanceOf(ApiException.class);
	}

}
