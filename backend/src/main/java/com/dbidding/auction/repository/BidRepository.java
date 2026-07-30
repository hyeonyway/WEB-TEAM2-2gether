package com.dbidding.auction.repository;

import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid, Long> {
    Optional<Bid> findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(Integer auctionId, BidStatus status);

    Optional<Bid> findFirstByAuctionIdAndBidderIdOrderByCreatedAtDesc(Integer auctionId, Integer bidderId);

    Optional<Bid> findFirstByBidderIdAndAuctionIdAndIdempotencyKey(
            Integer bidderId,
            Integer auctionId,
            String idempotencyKey
    );

    Page<Bid> findByAuctionIdOrderByCreatedAtDescIdDesc(Integer auctionId, Pageable pageable);

    List<Bid> findByAuctionIdInAndBidderIdOrderByCreatedAtDesc(Collection<Integer> auctionIds, Integer bidderId);

    List<Bid> findByBidderIdOrderByCreatedAtDescIdDesc(Integer bidderId);
}
