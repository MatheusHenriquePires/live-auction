package com.matheuspires.auction.auction;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateAuctionRequest(@NotBlank @Size(max = 140) String title, @Size(max = 5000) String description,
		@Size(max = 500) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String imageUrl,
		@Schema(description = "Cents") @NotNull @Positive @Max(MAX_CENTS) Long startingPrice,
		@Schema(description = "Cents") @NotNull @Positive @Max(MAX_CENTS) Long minIncrement,
		@Schema(description = "UTC; omit or send a past/now value to start immediately") Instant startsAt,
		@Schema(description = "UTC") @NotNull Instant endsAt) {

	/** 100 million in currency units; keeps every sum far away from long overflow. */
	public static final long MAX_CENTS = 10_000_000_000L;

}
