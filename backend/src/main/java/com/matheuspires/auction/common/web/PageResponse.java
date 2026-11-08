package com.matheuspires.auction.common.web;

import java.util.List;

/** Stable JSON shape for paged results (independent of Spring Data's internal page format). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
		int totalPages = (int) ((totalElements + size - 1) / size);
		return new PageResponse<>(content, page, size, totalElements, totalPages);
	}

}
