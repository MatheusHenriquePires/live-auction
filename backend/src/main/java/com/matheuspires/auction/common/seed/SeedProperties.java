package com.matheuspires.auction.common.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auction.seed")
public record SeedProperties(boolean enabled, String adminPassword, String botPassword) {

	@Override
	public String toString() {
		return "SeedProperties[enabled=" + enabled + ", adminPassword=***, botPassword=***]";
	}

}
