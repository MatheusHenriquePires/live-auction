package com.matheuspires.auction.auction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.matheuspires.auction.common.web.PageResponse;
import com.matheuspires.auction.user.Role;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of auctions: plain SQL with joins for display names, separate from the JPA write model.
 * Detail reads run in REPEATABLE READ so the auction row and its recent bids come from one snapshot.
 */
@Repository
public class AuctionQueries {

	private static final int RECENT_BIDS = 20;

	private static final String SELECT_AUCTION = """
			SELECT a.id, a.title, a.description, a.image_url, a.status, a.starting_price, a.min_increment,
			       a.current_price, a.bid_count, a.starts_at, a.ends_at,
			       a.seller_id, s.display_name AS seller_name,
			       a.leader_id, l.display_name AS leader_name,
			       a.winner_id, w.display_name AS winner_name
			FROM auctions a
			JOIN users s ON s.id = a.seller_id
			LEFT JOIN users l ON l.id = a.leader_id
			LEFT JOIN users w ON w.id = a.winner_id
			""";

	private static final String SELECT_BIDS = """
			SELECT b.id, b.amount, b.placed_at, b.bidder_id, u.display_name, u.role
			FROM bids b
			JOIN users u ON u.id = b.bidder_id
			WHERE b.auction_id = :auctionId
			ORDER BY b.amount DESC
			LIMIT :limit OFFSET :offset
			""";

	private final JdbcClient jdbc;

	private final Clock clock;

	AuctionQueries(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<AuctionSummary> list(AuctionStatus status, int page, int size, AuctionSort sort) {
		String where = status == null ? "" : "WHERE a.status = :status ";
		Map<String, Object> params = new HashMap<>();
		if (status != null) {
			params.put("status", status.name());
		}
		long total = jdbc.sql("SELECT count(*) FROM auctions a " + where).params(params).query(Long.class).single();
		List<AuctionSummary> content = jdbc
			.sql(SELECT_AUCTION + where + "ORDER BY " + sort.toSql() + ", a.id LIMIT :limit OFFSET :offset")
			.params(params)
			.param("limit", size)
			.param("offset", (long) page * size)
			.query((rs, rowNum) -> toSummary(rs))
			.list();
		return PageResponse.of(content, page, size, total);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public Optional<AuctionDetail> findDetail(UUID id) {
		Instant serverTime = clock.instant();
		Optional<AuctionDetail> withoutBids = jdbc.sql(SELECT_AUCTION + "WHERE a.id = :id")
			.param("id", id)
			.query((rs, rowNum) -> toDetail(rs, List.of(), serverTime))
			.optional();
		return withoutBids.map(detail -> withBids(detail, bidsPage(id, 0, RECENT_BIDS)));
	}

	@Transactional(readOnly = true)
	public PageResponse<BidView> bids(UUID auctionId, int page, int size) {
		long total = jdbc.sql("SELECT count(*) FROM bids WHERE auction_id = :auctionId")
			.param("auctionId", auctionId)
			.query(Long.class)
			.single();
		return PageResponse.of(bidsPage(auctionId, page, size), page, size, total);
	}

	public boolean exists(UUID id) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM auctions WHERE id = :id)")
			.param("id", id)
			.query(Boolean.class)
			.single();
	}

	private List<BidView> bidsPage(UUID auctionId, int page, int size) {
		return jdbc.sql(SELECT_BIDS)
			.param("auctionId", auctionId)
			.param("limit", size)
			.param("offset", (long) page * size)
			.query((rs, rowNum) -> toBid(rs))
			.list();
	}

	private static AuctionSummary toSummary(ResultSet rs) throws SQLException {
		long startingPrice = rs.getLong("starting_price");
		long currentPrice = rs.getLong("current_price");
		long minIncrement = rs.getLong("min_increment");
		int bidCount = rs.getInt("bid_count");
		return new AuctionSummary(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("image_url"),
				AuctionStatus.valueOf(rs.getString("status")), currentPrice,
				AuctionRules.minimumNextBid(startingPrice, currentPrice, minIncrement, bidCount), bidCount,
				instant(rs, "starts_at"), instant(rs, "ends_at"), rs.getObject("seller_id", UUID.class),
				rs.getString("seller_name"), rs.getObject("leader_id", UUID.class), rs.getString("leader_name"));
	}

	private static AuctionDetail toDetail(ResultSet rs, List<BidView> bids, Instant serverTime) throws SQLException {
		long startingPrice = rs.getLong("starting_price");
		long currentPrice = rs.getLong("current_price");
		long minIncrement = rs.getLong("min_increment");
		int bidCount = rs.getInt("bid_count");
		return new AuctionDetail(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("description"),
				rs.getString("image_url"), AuctionStatus.valueOf(rs.getString("status")), startingPrice, minIncrement,
				currentPrice, AuctionRules.minimumNextBid(startingPrice, currentPrice, minIncrement, bidCount),
				bidCount, instant(rs, "starts_at"), instant(rs, "ends_at"), rs.getObject("seller_id", UUID.class),
				rs.getString("seller_name"), rs.getObject("leader_id", UUID.class), rs.getString("leader_name"),
				rs.getObject("winner_id", UUID.class), rs.getString("winner_name"), serverTime, bids);
	}

	private static AuctionDetail withBids(AuctionDetail d, List<BidView> bids) {
		return new AuctionDetail(d.id(), d.title(), d.description(), d.imageUrl(), d.status(), d.startingPrice(),
				d.minIncrement(), d.currentPrice(), d.minimumNextBid(), d.bidCount(), d.startsAt(), d.endsAt(),
				d.sellerId(), d.sellerDisplayName(), d.leaderId(), d.leaderDisplayName(), d.winnerId(),
				d.winnerDisplayName(), d.serverTime(), bids);
	}

	private static BidView toBid(ResultSet rs) throws SQLException {
		return new BidView(rs.getObject("id", UUID.class), rs.getLong("amount"), rs.getObject("bidder_id", UUID.class),
				rs.getString("display_name"), Role.BOT.name().equals(rs.getString("role")), instant(rs, "placed_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

}
