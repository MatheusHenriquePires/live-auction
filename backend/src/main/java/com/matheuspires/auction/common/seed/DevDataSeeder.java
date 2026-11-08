package com.matheuspires.auction.common.seed;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.matheuspires.auction.auction.AuctionService;
import com.matheuspires.auction.auction.CreateAuctionRequest;
import com.matheuspires.auction.user.Role;
import com.matheuspires.auction.user.UserAccount;
import com.matheuspires.auction.user.UserRepository;
import com.matheuspires.auction.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data: one admin, five bot users and eight auctions with staggered end times. Runs only when
 * {@code auction.seed.enabled=true} and the database has no users, so restarts never duplicate data.
 * Passwords come from the environment; nothing is hard-coded.
 */
@Component
@ConditionalOnProperty(prefix = "auction.seed", name = "enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

	public static final String ADMIN_EMAIL = "admin@example.com";

	static final List<String> BOT_NAMES = List.of("Ana", "Bruno", "Carla", "Diego", "Elena");

	private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

	private static final List<Sample> SAMPLES = List.of(
			new Sample("Vintage 35mm film camera", "Fully working rangefinder from the 1970s, recently serviced.",
					15_000, 500, Duration.ZERO, Duration.ofMinutes(3)),
			new Sample("Mechanical keyboard", "75% layout, hot-swap sockets, tactile switches and PBT keycaps.",
					25_000, 1_000, Duration.ZERO, Duration.ofMinutes(10)),
			new Sample("Signed football jersey", "Match-worn jersey with a certificate of authenticity.", 50_000,
					2_500, Duration.ZERO, Duration.ofMinutes(30)),
			new Sample("Espresso machine", "Dual boiler, PID temperature control, barely used.", 120_000, 5_000,
					Duration.ZERO, Duration.ofHours(2)),
			new Sample("Limited edition sneakers", "Size 42, never worn, original box.", 80_000, 2_000, Duration.ZERO,
					Duration.ofHours(6)),
			new Sample("Electric guitar", "Alder body, maple neck, hard case included.", 180_000, 5_000,
					Duration.ZERO, Duration.ofHours(24)),
			new Sample("Antique pocket watch", "Silver case, hand-wound movement, keeps good time.", 60_000, 2_000,
					Duration.ofMinutes(3), Duration.ofMinutes(20)),
			new Sample("Compact 4K drone", "Under 250 g, three batteries, foldable.", 90_000, 2_500,
					Duration.ofMinutes(10), Duration.ofHours(1)));

	private final UserRepository users;

	private final UserService userService;

	private final AuctionService auctionService;

	private final SeedProperties properties;

	private final Clock clock;

	DevDataSeeder(UserRepository users, UserService userService, AuctionService auctionService,
			SeedProperties properties, Clock clock) {
		this.users = users;
		this.userService = userService;
		this.auctionService = auctionService;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		seed();
	}

	void seed() {
		if (users.count() > 0) {
			log.info("Seed skipped: the database already has users");
			return;
		}
		String adminPassword = required(properties.adminPassword(), "SEED_ADMIN_PASSWORD");
		String botPassword = required(properties.botPassword(), "SEED_BOT_PASSWORD");
		UserAccount admin = userService.create(ADMIN_EMAIL, "Admin", adminPassword, Role.ADMIN);
		for (int i = 0; i < BOT_NAMES.size(); i++) {
			userService.create("bot" + (i + 1) + "@example.com", BOT_NAMES.get(i), botPassword, Role.BOT);
		}
		Instant now = clock.instant();
		SAMPLES.forEach(sample -> auctionService.create(admin.getId(), sample.toRequest(now)));
		log.info("Seeded 1 admin, {} bots and {} auctions", BOT_NAMES.size(), SAMPLES.size());
	}

	private static String required(String value, String variable) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("auction.seed.enabled=true requires " + variable + " to be set");
		}
		return value;
	}

	private record Sample(String title, String description, long startingPrice, long minIncrement,
			Duration startsIn, Duration duration) {

		CreateAuctionRequest toRequest(Instant now) {
			Instant startsAt = now.plus(startsIn);
			return new CreateAuctionRequest(title, description, null, startingPrice, minIncrement,
					startsIn.isZero() ? null : startsAt, startsAt.plus(duration));
		}

	}

}
