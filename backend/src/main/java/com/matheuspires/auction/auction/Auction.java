package com.matheuspires.auction.auction;

import java.time.Instant;
import java.util.UUID;

import com.matheuspires.auction.common.error.ApiException;
import com.matheuspires.auction.common.error.ErrorCode;
import com.matheuspires.auction.common.persistence.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Auction aggregate. Amounts are integer cents. {@code version} enables optimistic locking, so a
 * cancel racing a bid cannot both succeed.
 */
@Entity
@Table(name = "auctions")
public class Auction extends AssignedIdEntity {

	@Column(name = "seller_id", nullable = false, updatable = false)
	private UUID sellerId;

	@Column(nullable = false, length = 140)
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Column(name = "starting_price", nullable = false, updatable = false)
	private long startingPrice;

	@Column(name = "min_increment", nullable = false, updatable = false)
	private long minIncrement;

	@Column(name = "current_price", nullable = false)
	private long currentPrice;

	@Column(name = "leader_id")
	private UUID leaderId;

	@Column(name = "bid_count", nullable = false)
	private int bidCount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuctionStatus status;

	@Column(name = "starts_at", nullable = false, updatable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Column(name = "winner_id")
	private UUID winnerId;

	@Version
	private Long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Auction() {
	}

	private Auction(UUID id, UUID sellerId, String title, String description, String imageUrl, long startingPrice,
			long minIncrement, Instant startsAt, Instant endsAt, Instant createdAt) {
		super(id);
		this.sellerId = sellerId;
		this.title = title;
		this.description = description;
		this.imageUrl = imageUrl;
		this.startingPrice = startingPrice;
		this.minIncrement = minIncrement;
		this.currentPrice = startingPrice;
		this.status = AuctionStatus.SCHEDULED;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.createdAt = createdAt;
	}

	/** Creates a SCHEDULED auction; schedule validation happens in {@link AuctionRules}. */
	public static Auction schedule(UUID id, UUID sellerId, String title, String description, String imageUrl,
			long startingPrice, long minIncrement, Instant startsAt, Instant endsAt, Instant now) {
		return new Auction(id, sellerId, title, description, imageUrl, startingPrice, minIncrement, startsAt, endsAt,
				now);
	}

	public void activate() {
		if (status != AuctionStatus.SCHEDULED) {
			throw new IllegalStateException("Only scheduled auctions can be activated, status was " + status);
		}
		status = AuctionStatus.ACTIVE;
	}

	public void cancel() {
		if (status != AuctionStatus.SCHEDULED && status != AuctionStatus.ACTIVE) {
			throw new ApiException(ErrorCode.AUCTION_NOT_CANCELLABLE,
					"Only scheduled or active auctions can be cancelled (status is " + status + ")");
		}
		if (bidCount > 0) {
			throw new ApiException(ErrorCode.AUCTION_NOT_CANCELLABLE, "Auctions that already have bids cannot be cancelled");
		}
		status = AuctionStatus.CANCELLED;
	}

	public boolean isSeller(UUID userId) {
		return sellerId.equals(userId);
	}

	public UUID getSellerId() {
		return sellerId;
	}

	public String getTitle() {
		return title;
	}

	public long getStartingPrice() {
		return startingPrice;
	}

	public long getMinIncrement() {
		return minIncrement;
	}

	public long getCurrentPrice() {
		return currentPrice;
	}

	public UUID getLeaderId() {
		return leaderId;
	}

	public int getBidCount() {
		return bidCount;
	}

	public AuctionStatus getStatus() {
		return status;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public UUID getWinnerId() {
		return winnerId;
	}

}
