package com.dbidding.auction.repository;

import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, Long> {
    Optional<Bid> findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(Integer auctionId, BidStatus status);

    Optional<Bid> findByAuctionIdAndStatus(Integer auctionId, BidStatus status);

    List<Bid> findByAuctionIdInAndStatus(Collection<Integer> auctionIds, BidStatus status);

    @Query("select b.auction.id from Bid b where b.status = :status")
    List<Integer> findAuctionIdsByStatus(@Param("status") BidStatus status);

    @Query(
            value = """
                    SELECT b1.* FROM bids b1
                    WHERE b1.auction_id IN (:auctionIds)
                      AND b1.id = (
                          SELECT MAX(b2.id) FROM bids b2
                          WHERE b2.auction_id = b1.auction_id AND b2.user_id = b1.user_id
                      )
                    """,
            nativeQuery = true
    )
    List<Bid> findLatestBidPerBidderByAuctionIdIn(@Param("auctionIds") Collection<Integer> auctionIds);

    Optional<Bid> findFirstByAuctionIdAndStatusInOrderByBidPriceDescCreatedAtAsc(
            Integer auctionId,
            Collection<BidStatus> statuses
    );

    Optional<Bid> findFirstByAuctionIdAndBidderIdOrderByCreatedAtDescIdDesc(Integer auctionId, Integer bidderId);

    Optional<Bid> findFirstByBidderIdAndAuctionIdAndIdempotencyKey(
            Integer bidderId,
            Integer auctionId,
            String idempotencyKey
    );

    Page<Bid> findByAuctionIdOrderByCreatedAtDescIdDesc(Integer auctionId, Pageable pageable);

    List<Bid> findByAuctionIdInAndBidderIdOrderByCreatedAtDescIdDesc(Collection<Integer> auctionIds, Integer bidderId);

    List<Bid> findByBidderIdOrderByCreatedAtDescIdDesc(Integer bidderId);
}
