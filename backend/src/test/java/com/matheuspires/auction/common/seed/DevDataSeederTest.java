package com.matheuspires.auction.common.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.Collections;
import java.util.List;

import com.matheuspires.auction.IntegrationTest;
import com.matheuspires.auction.auction.AuctionService;
import com.matheuspires.auction.user.Role;
import com.matheuspires.auction.user.UserAccount;
import com.matheuspires.auction.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DevDataSeederTest extends IntegrationTest {

	@Autowired
	UserRepository users;

	@Autowired
	AuctionService auctionService;

	@Autowired
	Clock clock;

	@Test
	void seedsDemoDataOnlyOnce() {
		DevDataSeeder seeder = seeder("admin-password", "bot-password");

		seeder.seed();
		seeder.seed();

		assertThat(users.count()).isEqualTo(6);
		assertThat(users.findByEmail(DevDataSeeder.ADMIN_EMAIL)).get()
			.extracting(UserAccount::getRole)
			.isEqualTo(Role.ADMIN);
		assertThat(users.findByEmail("bot1@example.com")).get().extracting(UserAccount::getRole).isEqualTo(Role.BOT);
		List<String> statuses = jdbc.queryForList("SELECT status FROM auctions", String.class);
		assertThat(statuses).hasSize(8).containsOnly("ACTIVE", "SCHEDULED");
		assertThat(Collections.frequency(statuses, "ACTIVE")).isEqualTo(6);
	}

	@Test
	void refusesToSeedWithoutPasswords() {
		assertThatThrownBy(() -> seeder("", "bot-password").seed()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("SEED_ADMIN_PASSWORD");
		assertThat(users.count()).isZero();
	}

	private DevDataSeeder seeder(String adminPassword, String botPassword) {
		return new DevDataSeeder(users, userService, auctionService,
				new SeedProperties(true, adminPassword, botPassword), clock);
	}

}
