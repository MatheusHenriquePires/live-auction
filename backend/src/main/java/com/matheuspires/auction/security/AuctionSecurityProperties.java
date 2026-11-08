package com.matheuspires.auction.security;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Security settings, validated at startup: the app refuses to boot with a weak or missing JWT secret. */
@Validated
@ConfigurationProperties("auction.security")
public record AuctionSecurityProperties(@Valid @NotNull JwtSettings jwt, @Min(4) @Max(31) int bcryptStrength,
		@NotEmpty List<String> corsAllowedOrigins) {

	public record JwtSettings(
			@NotBlank @Size(min = 32, message = "must have at least 32 characters (256 bits) for HS256") String secret,
			@NotBlank String issuer, @NotNull Duration accessTokenTtl, @NotNull Duration refreshTokenTtl) {

		@Override
		public String toString() {
			return "JwtSettings[secret=***, issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl
					+ ", refreshTokenTtl=" + refreshTokenTtl + "]";
		}

	}

}
