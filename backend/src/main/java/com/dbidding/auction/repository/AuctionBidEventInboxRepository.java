package com.dbidding.auction.repository;

import com.dbidding.auction.domain.AuctionBidEventInbox;
import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionBidEventInboxRepository extends JpaRepository<AuctionBidEventInbox, Long> {
    Optional<AuctionBidEventInbox> findByStreamId(String streamId);

    List<AuctionBidEventInbox> findByStreamIdIn(Collection<String> streamIds);

    boolean existsByProjectionStatus(AuctionBidEventProjectionStatus projectionStatus);

    long countByProjectionStatus(AuctionBidEventProjectionStatus projectionStatus);

    Optional<AuctionBidEventInbox> findFirstByProjectionStatusInOrderByIdAsc(Collection<AuctionBidEventProjectionStatus> statuses);

    Optional<AuctionBidEventInbox> findFirstByProjectionStatusOrderByProcessedAtDesc(AuctionBidEventProjectionStatus status);

    Page<AuctionBidEventInbox> findByProjectionStatusInOrderByIdAsc(Collection<AuctionBidEventProjectionStatus> statuses, Pageable pageable);
}
