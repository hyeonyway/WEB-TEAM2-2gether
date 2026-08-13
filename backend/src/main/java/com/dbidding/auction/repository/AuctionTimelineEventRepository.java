package com.dbidding.auction.repository;

import com.dbidding.auction.domain.AuctionTimelineEvent;
import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionTimelineEventRepository extends JpaRepository<AuctionTimelineEvent, Long> {
    Optional<AuctionTimelineEvent> findByStreamId(String streamId);

    List<AuctionTimelineEvent> findByStreamIdIn(Collection<String> streamIds);

    boolean existsByProjectionStatus(AuctionBidEventProjectionStatus projectionStatus);

    long countByProjectionStatus(AuctionBidEventProjectionStatus projectionStatus);

    Optional<AuctionTimelineEvent> findFirstByProjectionStatusInOrderByIdAsc(Collection<AuctionBidEventProjectionStatus> statuses);

    Optional<AuctionTimelineEvent> findFirstByProjectionStatusOrderByProcessedAtDesc(AuctionBidEventProjectionStatus status);

    Page<AuctionTimelineEvent> findByProjectionStatusInOrderByIdAsc(Collection<AuctionBidEventProjectionStatus> statuses, Pageable pageable);

    Page<AuctionTimelineEvent> findByProjectionStatusOrderByProcessedAtDesc(AuctionBidEventProjectionStatus status, Pageable pageable);

    Optional<AuctionTimelineEvent> findFirstByProjectionStatusOrderByIdAsc(AuctionBidEventProjectionStatus status);
}
