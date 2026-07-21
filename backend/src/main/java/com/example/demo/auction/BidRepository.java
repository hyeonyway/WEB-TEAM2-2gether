package com.example.demo.auction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {
	Optional<Bid> findFirstByAuctionIdOrderByAmountDescIdAsc(Long auctionId);
}
