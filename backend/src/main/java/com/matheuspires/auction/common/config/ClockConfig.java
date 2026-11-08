package com.matheuspires.auction.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClockConfig {

	/**
	 * The server clock is the only clock. Every time-dependent component receives this bean, so tests
	 * can replace it and no code calls {@code Instant.now()} directly.
	 */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
