package com.matheuspires.auction.auction;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.matheuspires.auction.IntegrationTest;
import com.matheuspires.auction.common.Ids;
import com.matheuspires.auction.user.Role;
import com.matheuspires.auction.user.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class AuctionApiTest extends IntegrationTest {

	@Autowired
	AuctionService auctionService;

	@Autowired
	Clock clock;

	private UserAccount seller;

	private UserAccount bidder;

	@BeforeEach
	void createUsers() {
		seller = createUser("Seller", Role.USER);
		bidder = createUser("Bidder", Role.USER);
	}

	@Test
	void creatingRequiresAuthentication() throws Exception {
		mvc.perform(post("/api/v1/auctions").contentType(APPLICATION_JSON)
			.content(body(null, clock.instant().plus(Duration.ofMinutes(10)))))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void auctionStartingNowIsActiveAndPubliclyVisible() throws Exception {
		String location = create(body(null, clock.instant().plus(Duration.ofMinutes(10))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.currentPrice").value(10_000))
			.andExpect(jsonPath("$.minimumNextBid").value(10_000))
			.andExpect(jsonPath("$.sellerDisplayName").value("Seller"))
			.andReturn()
			.getResponse()
			.getHeader("Location");

		mvc.perform(get(location))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Vintage camera"))
			.andExpect(jsonPath("$.serverTime").isNotEmpty())
			.andExpect(jsonPath("$.bidCount").value(0))
			.andExpect(jsonPath("$.recentBids").isEmpty());
	}

	@Test
	void auctionStartingLaterIsScheduled() throws Exception {
		Instant startsAt = clock.instant().plus(Duration.ofHours(1));

		create(body(startsAt, startsAt.plus(Duration.ofHours(1)))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("SCHEDULED"));
	}

	@Test
	void scheduleRulesAreEnforced() throws Exception {
		Instant now = clock.instant();

		create(body(now.plus(Duration.ofHours(2)), now.plus(Duration.ofHours(1)))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		create(body(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void fieldsAreValidated() throws Exception {
		create("""
				{"title": "", "startingPrice": -5, "minIncrement": 0, "imageUrl": "javascript:alert(1)"}
				""").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[*].field",
					hasItems("title", "startingPrice", "minIncrement", "imageUrl", "endsAt")));
	}

	@Test
	void listFiltersByStatusPaginatesAndSorts() throws Exception {
		Instant now = clock.instant();
		UUID endsFirst = createAuction(null, now.plus(Duration.ofMinutes(5)));
		createAuction(null, now.plus(Duration.ofMinutes(10)));
		UUID endsLast = createAuction(null, now.plus(Duration.ofMinutes(15)));
		createAuction(now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(2)));

		mvc.perform(get("/api/v1/auctions").param("status", "ACTIVE").param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.content.length()").value(2))
			.andExpect(jsonPath("$.content[0].id").value(endsFirst.toString()));

		mvc.perform(get("/api/v1/auctions").param("status", "ACTIVE").param("sort", "endsAt,desc"))
			.andExpect(jsonPath("$.content[0].id").value(endsLast.toString()));

		mvc.perform(get("/api/v1/auctions")).andExpect(jsonPath("$.totalElements").value(4));
	}

	@Test
	void invalidListParametersAreRejected() throws Exception {
		mvc.perform(get("/api/v1/auctions").param("sort", "title"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(get("/api/v1/auctions").param("size", "500"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(get("/api/v1/auctions").param("status", "NOPE")).andExpect(status().isBadRequest());
	}

	@Test
	void unknownAndMalformedIdsAreHandled() throws Exception {
		mvc.perform(get("/api/v1/auctions/" + UUID.randomUUID()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mvc.perform(get("/api/v1/auctions/" + UUID.randomUUID() + "/bids")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/auctions/not-a-uuid"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void detailListsHighestBidsFirstAndFlagsBots() throws Exception {
		UserAccount bot = createUser("Robo", Role.BOT);
		UUID id = createAuction(null, clock.instant().plus(Duration.ofMinutes(30)));
		insertBid(id, bidder, 10_000);
		insertBid(id, bot, 10_500);

		mvc.perform(get("/api/v1/auctions/" + id))
			.andExpect(jsonPath("$.bidCount").value(2))
			.andExpect(jsonPath("$.currentPrice").value(10_500))
			.andExpect(jsonPath("$.minimumNextBid").value(11_000))
			.andExpect(jsonPath("$.leaderDisplayName").value("Robo"))
			.andExpect(jsonPath("$.recentBids[0].amount").value(10_500))
			.andExpect(jsonPath("$.recentBids[0].bot").value(true))
			.andExpect(jsonPath("$.recentBids[1].bidderDisplayName").value("Bidder"))
			.andExpect(jsonPath("$.recentBids[1].bot").value(false));

		mvc.perform(get("/api/v1/auctions/" + id + "/bids").param("size", "1"))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].amount").value(10_500));
	}

	@Test
	void sellerCancelsOnceAndOnlyOnce() throws Exception {
		UUID id = createAuction(null, clock.instant().plus(Duration.ofMinutes(30)));

		cancel(id, seller).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
		cancel(id, seller).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AUCTION_NOT_CANCELLABLE"));
	}

	@Test
	void onlyTheSellerOrAnAdminCanCancel() throws Exception {
		UUID id = createAuction(null, clock.instant().plus(Duration.ofMinutes(30)));

		cancel(id, bidder).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		cancel(id, createUser("Boss", Role.ADMIN)).andExpect(status().isOk());
		cancel(UUID.randomUUID(), seller).andExpect(status().isNotFound());
	}

	@Test
	void auctionWithBidsCannotBeCancelled() throws Exception {
		UUID id = createAuction(null, clock.instant().plus(Duration.ofMinutes(30)));
		insertBid(id, bidder, 10_000);

		cancel(id, seller).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AUCTION_NOT_CANCELLABLE"));
	}

	private ResultActions create(String json) throws Exception {
		return mvc.perform(post("/api/v1/auctions").header(AUTHORIZATION, bearer(seller))
			.contentType(APPLICATION_JSON)
			.content(json));
	}

	private ResultActions cancel(UUID id, UserAccount user) throws Exception {
		return mvc.perform(post("/api/v1/auctions/" + id + "/cancel").header(AUTHORIZATION, bearer(user)));
	}

	private UUID createAuction(Instant startsAt, Instant endsAt) {
		return auctionService.create(seller.getId(),
				new CreateAuctionRequest("Item", null, null, 10_000L, 500L, startsAt, endsAt));
	}

	/** Simulates an accepted bid at the database level (the bid engine arrives in phase 2). */
	private void insertBid(UUID auctionId, UserAccount user, long amount) {
		jdbc.update("INSERT INTO bids (id, auction_id, bidder_id, amount) VALUES (?, ?, ?, ?)", Ids.newId(), auctionId,
				user.getId(), amount);
		jdbc.update("UPDATE auctions SET current_price = ?, leader_id = ?, bid_count = bid_count + 1 WHERE id = ?",
				amount, user.getId(), auctionId);
	}

	private static String body(Instant startsAt, Instant endsAt) {
		return """
				{"title": "Vintage camera", "description": "Works fine", "startingPrice": 10000, "minIncrement": 500,
				 "startsAt": %s, "endsAt": "%s"}
				""".formatted(startsAt == null ? "null" : "\"" + startsAt + "\"", endsAt);
	}

}
