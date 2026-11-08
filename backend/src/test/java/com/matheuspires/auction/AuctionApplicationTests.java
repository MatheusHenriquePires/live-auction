package com.matheuspires.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics // metrics export is disabled in tests by default
@Import(TestcontainersConfiguration.class)
class AuctionApplicationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void flywayCreatesTheSchema() {
		var tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
		assertThat(tables).contains("users", "auctions", "bids", "outbox_events", "refresh_tokens", "shedlock");
	}

	@Test
	void healthIsUpWithAllInfrastructure() throws Exception {
		mvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void prometheusEndpointIsExposed() throws Exception {
		mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
	}

	@Test
	void openApiDocsAreServed() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Live Auction API"));
	}

}
