package com.matheuspires.auction.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	/** Name referenced by {@code @SecurityRequirement} on protected endpoints. */
	public static final String BEARER_SCHEME = "bearer-jwt";

	@Bean
	OpenAPI liveAuctionOpenApi() {
		return new OpenAPI()
			.info(new Info().title("Live Auction API")
				.version("v1")
				.description("Real-time auction platform. Amounts are integer cents; timestamps are UTC. "
						+ "Call POST /api/v1/auth/login, then use Authorize with the access token."))
			.components(new Components().addSecuritySchemes(BEARER_SCHEME,
					new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
	}

}
