package com.matheuspires.auction.auction;

import java.net.URI;
import java.util.UUID;

import com.matheuspires.auction.common.config.OpenApiConfig;
import com.matheuspires.auction.common.error.ApiException;
import com.matheuspires.auction.common.web.PageResponse;
import com.matheuspires.auction.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auctions")
@Tag(name = "Auctions")
class AuctionController {

	private final AuctionService auctionService;

	private final AuctionQueries queries;

	AuctionController(AuctionService auctionService, AuctionQueries queries) {
		this.auctionService = auctionService;
		this.queries = queries;
	}

	@GetMapping
	@Operation(summary = "List auctions (public)", description = "sort: endsAt|startsAt|createdAt|currentPrice|bidCount[,asc|desc]")
	PageResponse<AuctionSummary> list(@RequestParam(required = false) AuctionStatus status,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "endsAt,asc") String sort) {
		return queries.list(status, page, size, AuctionSort.parse(sort));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Auction detail with server time and the 20 highest bids (public)")
	AuctionDetail get(@PathVariable UUID id) {
		return queries.findDetail(id).orElseThrow(() -> ApiException.notFound("Auction"));
	}

	@PostMapping
	@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
	@Operation(summary = "Create an auction (amounts in cents)")
	ResponseEntity<AuctionDetail> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateAuctionRequest request) {
		UUID id = auctionService.create(AuthenticatedUser.from(jwt).id(), request);
		return ResponseEntity.created(URI.create("/api/v1/auctions/" + id)).body(get(id));
	}

	@PostMapping("/{id}/cancel")
	@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
	@Operation(summary = "Cancel an auction without bids (seller or admin)")
	AuctionDetail cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
		auctionService.cancel(id, AuthenticatedUser.from(jwt));
		return get(id);
	}

	@GetMapping("/{id}/bids")
	@Operation(summary = "Bid history, highest first (public)")
	PageResponse<BidView> bids(@PathVariable UUID id, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		if (!queries.exists(id)) {
			throw ApiException.notFound("Auction");
		}
		return queries.bids(id, page, size);
	}

}
