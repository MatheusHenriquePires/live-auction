package com.matheuspires.auction;

import com.matheuspires.auction.security.TokenService;
import com.matheuspires.auction.user.Role;
import com.matheuspires.auction.user.UserAccount;
import com.matheuspires.auction.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for integration tests: one shared Spring context backed by real Postgres, Redis and
 * RabbitMQ containers. Every test starts from empty tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

	protected static final String PASSWORD = "password123";

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected UserService userService;

	@Autowired
	protected TokenService tokenService;

	@BeforeEach
	void resetDatabase() {
		jdbc.execute("TRUNCATE bids, outbox_events, refresh_tokens, auctions, users CASCADE");
	}

	/** Creates a user with e-mail {@code <displayName lowercased>@example.com} and {@link #PASSWORD}. */
	protected UserAccount createUser(String displayName, Role role) {
		return userService.create(displayName.toLowerCase() + "@example.com", displayName, PASSWORD, role);
	}

	protected String bearer(UserAccount user) {
		return "Bearer " + tokenService.issueAccessToken(user.getId(), user.getEmail(), user.getRole());
	}

}
