package com.matheuspires.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matheuspires.auction.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuctionApplicationTests extends IntegrationTest {

	@Test
	void flywayCreatesTheSchema() {
		var tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
		assertThat(tables).contains("users", "auctions", "bids", "outbox_events", "refresh_tokens", "shedlock");
	}

	@Test
	void healthIsPublicAndUpWithAllInfrastructure() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void prometheusIsRestrictedToAdmins() throws Exception {
		mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
		mvc.perform(get("/actuator/prometheus").header(AUTHORIZATION, bearer(createUser("Viewer", Role.USER))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(get("/actuator/prometheus").header(AUTHORIZATION, bearer(createUser("Admin", Role.ADMIN))))
			.andExpect(status().isOk());
	}

	@Test
	void openApiDocsArePublic() throws Exception {
		mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("Live Auction API"));
	}

	@Test
	void unknownRouteIsAProblemNotAStackTrace() throws Exception {
		mvc.perform(get("/api/v1/does-not-exist").header(AUTHORIZATION, bearer(createUser("Lost", Role.USER))))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.trace").doesNotExist());
	}

}
