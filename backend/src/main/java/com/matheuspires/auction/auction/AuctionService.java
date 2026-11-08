package com.matheuspires.auction.auction;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.matheuspires.auction.common.Ids;
import com.matheuspires.auction.common.error.ApiException;
import com.matheuspires.auction.common.error.ErrorCode;
import com.matheuspires.auction.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuctionService {

	private final AuctionRepository auctions;

	private final Clock clock;

	AuctionService(AuctionRepository auctions, Clock clock) {
		this.auctions = auctions;
		this.clock = clock;
	}

	/** Creates an auction; it starts ACTIVE when {@code startsAt} is omitted or not in the future. */
	@Transactional
	public UUID create(UUID sellerId, CreateAuctionRequest request) {
		Instant now = clock.instant();
		Instant startsAt = request.startsAt() == null ? now : request.startsAt();
		AuctionRules.validateSchedule(now, startsAt, request.endsAt());
		Auction auction = Auction.schedule(Ids.newId(), sellerId, request.title().strip(), request.description(),
				request.imageUrl(), request.startingPrice(), request.minIncrement(), startsAt, request.endsAt(), now);
		if (!startsAt.isAfter(now)) {
			auction.activate();
		}
		return auctions.save(auction).getId();
	}

	/** Seller or admin only, and only while there are no bids. The version check makes it race-safe. */
	@Transactional
	public void cancel(UUID auctionId, AuthenticatedUser requester) {
		Auction auction = auctions.findById(auctionId).orElseThrow(() -> ApiException.notFound("Auction"));
		if (!requester.isAdmin() && !auction.isSeller(requester.id())) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Only the seller or an admin can cancel this auction");
		}
		auction.cancel();
	}

}
