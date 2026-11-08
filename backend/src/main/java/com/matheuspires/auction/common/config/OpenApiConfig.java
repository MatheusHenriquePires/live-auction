package com.matheuspires.auction.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

	@Bean
	OpenAPI liveAuctionOpenApi() {
		return new OpenAPI().info(new Info()
				.title("Live Auction API")
				.version("v1")
				.description("Real-time auction platform. Amounts are integer cents; timestamps are UTC."));
	}

}
