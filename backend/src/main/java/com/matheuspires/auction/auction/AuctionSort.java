package com.matheuspires.auction.auction;

import java.util.Map;
import java.util.TreeSet;

import com.matheuspires.auction.common.error.ApiException;
import com.matheuspires.auction.common.error.ErrorCode;

/** Whitelisted sort for the auction list: {@code property[,asc|desc]}. Never interpolates user input. */
record AuctionSort(String column, boolean ascending) {

	private static final Map<String, String> COLUMNS = Map.of("endsAt", "a.ends_at", "startsAt", "a.starts_at",
			"createdAt", "a.created_at", "currentPrice", "a.current_price", "bidCount", "a.bid_count");

	static AuctionSort parse(String raw) {
		String[] parts = raw.split(",", -1);
		String column = COLUMNS.get(parts[0].strip());
		String direction = parts.length > 1 ? parts[1].strip().toLowerCase() : "asc";
		if (column == null || parts.length > 2 || !(direction.equals("asc") || direction.equals("desc"))) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR,
					"sort must be one of " + new TreeSet<>(COLUMNS.keySet()) + ", optionally followed by ,asc or ,desc");
		}
		return new AuctionSort(column, direction.equals("asc"));
	}

	String toSql() {
		return column + (ascending ? " ASC" : " DESC");
	}

}
