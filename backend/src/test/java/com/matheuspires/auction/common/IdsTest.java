package com.matheuspires.auction.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class IdsTest {

	@Test
	void generatesVersion7UuidsWithTheRfcVariant() {
		UUID id = Ids.newId();

		assertThat(id.version()).isEqualTo(7);
		assertThat(id.variant()).isEqualTo(2);
	}

	@Test
	void embedsTheCreationTimestamp() {
		long before = System.currentTimeMillis();
		UUID id = Ids.newId();
		long after = System.currentTimeMillis();

		assertThat(id.getMostSignificantBits() >>> 16).isBetween(before, after);
	}

	@Test
	void laterIdsSortAfterEarlierOnes() throws InterruptedException {
		UUID first = Ids.newId();
		Thread.sleep(2);
		UUID second = Ids.newId();

		assertThat(first).isLessThan(second);
	}

	@Test
	void doesNotRepeat() {
		Set<UUID> ids = new HashSet<>();
		for (int i = 0; i < 10_000; i++) {
			ids.add(Ids.newId());
		}

		assertThat(ids).hasSize(10_000);
	}

}
