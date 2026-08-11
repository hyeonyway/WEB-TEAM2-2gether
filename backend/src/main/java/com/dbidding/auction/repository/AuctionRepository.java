package com.dbidding.auction.repository;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionRepository extends JpaRepository<Auction, Integer> {
    @Query("""
            select a from Auction a
            where a.status in :statuses
              and (:activeOnly = false or a.closeTime > :now)
              and (:keyword = '' or lower(a.auctionName) like lower(concat('%', :keyword, '%')))
              and (:psaGrade is null or a.itemId in (
                    select c.id from CardMetadata c
                    where replace(upper(trim(c.psaGrade)), 'PSA ', '') =
                          replace(upper(trim(:psaGrade)), 'PSA ', '')
              ))
              and (
                    :cursorId is null
                    or (:sort = 'LATEST' and (
                        a.openTime < :openTimeCursor
                        or (a.openTime = :openTimeCursor and a.id < :cursorId)
                    ))
                    or (:sort = 'BID_COUNT' and (
                        a.bidCount < :bidCountCursor
                        or (a.bidCount = :bidCountCursor and a.id < :cursorId)
                    ))
                    or (:sort = 'PRICE_HIGH' and (
                        a.currentPrice < :priceCursor
                        or (a.currentPrice = :priceCursor and a.id < :cursorId)
                    ))
                    or (:sort = 'PRICE_LOW' and (
                        a.currentPrice > :priceCursor
                        or (a.currentPrice = :priceCursor and a.id < :cursorId)
                    ))
                    or (:sort = 'CHANGE_HIGH' and (
                        a.changeRateBasisPoints < :changeRateCursor
                        or (a.changeRateBasisPoints = :changeRateCursor and a.id < :cursorId)
                    ))
              )
            order by
              case when :sort = 'LATEST' then a.openTime end desc,
              case when :sort = 'BID_COUNT' then a.bidCount end desc,
              case when :sort = 'PRICE_HIGH' then a.currentPrice end desc,
              case when :sort = 'PRICE_LOW' then a.currentPrice end asc,
              case when :sort = 'CHANGE_HIGH' then a.changeRateBasisPoints end desc,
              a.id desc
            """)
    List<Auction> searchByCursor(
            @Param("keyword") String keyword,
            @Param("psaGrade") String psaGrade,
            @Param("statuses") Collection<AuctionStatus> statuses,
            @Param("sort") String sort,
            @Param("bidCountCursor") Integer bidCountCursor,
            @Param("priceCursor") Long priceCursor,
            @Param("changeRateCursor") Long changeRateCursor,
            @Param("openTimeCursor") Instant openTimeCursor,
            @Param("cursorId") Integer cursorId,
            @Param("activeOnly") boolean activeOnly,
            @Param("now") Instant now,
            Pageable pageable
    );

    Optional<Auction> findByIdAndStatusNot(Integer id, AuctionStatus status);

    Optional<Auction> findBySellerIdAndCreateIdempotencyKey(Integer sellerId, String createIdempotencyKey);

    List<Auction> findBySellerIdAndStatusOrderByCloseTimeDesc(Integer sellerId, AuctionStatus status);

    long countByItemIdAndStatusInAndCloseTimeAfter(
            Integer itemId,
            Collection<AuctionStatus> statuses,
            Instant closeTime
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id in :ids")
    List<Auction> findByIdInForUpdate(@Param("ids") Collection<Integer> ids);

    @Query("""
            select a.id from Auction a
            where a.status in :statuses
              and a.closeTime <= :now
            order by a.closeTime asc, a.id asc
            """)
    List<Integer> findDueAuctionIds(
            @Param("statuses") Collection<AuctionStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            select a from Auction a
            where a.status in :statuses
            order by a.closeTime asc, a.id asc
            """)
    List<Auction> findNextCloseTarget(
            @Param("statuses") Collection<AuctionStatus> statuses,
            Pageable pageable
    );

    List<Auction> findByStatusInAndOpenTimeGreaterThanEqual(
            Collection<AuctionStatus> statuses,
            Instant openTime
    );

    List<Auction> findByStatusInAndCloseTimeGreaterThanEqual(
            Collection<AuctionStatus> statuses,
            Instant closeTime
    );
}
