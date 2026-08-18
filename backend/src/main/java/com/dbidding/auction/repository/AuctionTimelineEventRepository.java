package com.dbidding.auction.repository;

import com.dbidding.auction.domain.AuctionTimelineEvent;
import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    Optional<AuctionTimelineEvent> findFirstByAuctionIdOrderByIdDesc(Integer auctionId);

    boolean existsByAuctionIdAndProjectionStatus(Integer auctionId, AuctionBidEventProjectionStatus projectionStatus);

    boolean existsByAuctionIdAndProjectionStatusIn(Integer auctionId, Collection<AuctionBidEventProjectionStatus> projectionStatuses);

    boolean existsByUserIdAndProjectionStatusIn(Integer userId, Collection<AuctionBidEventProjectionStatus> projectionStatuses);

    @Query("""
            SELECT DISTINCT t.auctionId FROM AuctionTimelineEvent t
            WHERE t.projectionStatus = :status AND t.auctionId IS NOT NULL
            """)
    List<Integer> findAuctionIdsWithStatus(@Param("status") AuctionBidEventProjectionStatus status);

    default List<Integer> findAuctionIdsWithError() {
        return findAuctionIdsWithStatus(AuctionBidEventProjectionStatus.ERROR);
    }

    @Query("""
            SELECT t FROM AuctionTimelineEvent t
            WHERE t.projectionStatus = :status
            AND (t.auctionId IS NULL OR t.auctionId NOT IN :blockedAuctionIds)
            ORDER BY t.id ASC
            """)
    List<AuctionTimelineEvent> findEligible(
            @Param("status") AuctionBidEventProjectionStatus status,
            @Param("blockedAuctionIds") Collection<Integer> blockedAuctionIds,
            Pageable pageable);

    default List<AuctionTimelineEvent> findEligiblePending(Collection<Integer> blockedAuctionIds, Pageable pageable) {
        return findEligible(AuctionBidEventProjectionStatus.PENDING, blockedAuctionIds, pageable);
    }
}
