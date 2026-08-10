package com.dbidding.auction.repository;

import com.dbidding.auction.domain.AuctionBidEventInbox;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionBidEventInboxRepository extends JpaRepository<AuctionBidEventInbox, Long> {
    Optional<AuctionBidEventInbox> findByStreamId(String streamId);
}
