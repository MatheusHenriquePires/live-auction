package com.matheuspires.auction.auction;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

}
