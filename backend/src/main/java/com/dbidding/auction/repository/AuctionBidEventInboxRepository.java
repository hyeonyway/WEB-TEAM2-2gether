package com.dbidding.auction.repository;

import com.dbidding.auction.domain.AuctionBidEventInbox;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionBidEventInboxRepository extends JpaRepository<AuctionBidEventInbox, Long> {
    Optional<AuctionBidEventInbox> findByStreamId(String streamId);

    List<AuctionBidEventInbox> findByStreamIdIn(Collection<String> streamIds);
}
