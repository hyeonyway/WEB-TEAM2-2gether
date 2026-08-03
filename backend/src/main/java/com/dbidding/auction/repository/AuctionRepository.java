package com.dbidding.auction.repository;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionRepository extends JpaRepository<Auction, Integer> {
    @Query("""
            select a from Auction a
            where a.status in :statuses
              and (:keyword = '' or lower(a.auctionName) like lower(concat('%', :keyword, '%')))
              and (:psaGrade is null or a.itemId in (
                    select c.id from CardMetadata c
                    where replace(upper(trim(c.psaGrade)), 'PSA ', '') =
                          replace(upper(trim(:psaGrade)), 'PSA ', '')
              ))
            order by
              case when :sort = 'BID_COUNT' then a.bidCount end desc,
              case when :sort = 'PRICE_HIGH' then a.currentPrice end desc,
              case when :sort = 'PRICE_LOW' then a.currentPrice end asc,
              a.id desc
            """)
    Page<Auction> search(
            @Param("keyword") String keyword,
            @Param("psaGrade") String psaGrade,
            @Param("statuses") Collection<AuctionStatus> statuses,
            @Param("sort") String sort,
            Pageable pageable
    );

    Optional<Auction> findByIdAndStatusNot(Integer id, AuctionStatus status);

    Optional<Auction> findBySellerIdAndCreateIdempotencyKey(Integer sellerId, String createIdempotencyKey);

    long countByItemIdAndStatusIn(Integer itemId, Collection<AuctionStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from Auction a
            where a.status in :statuses
              and a.closeTime <= :now
            order by a.closeTime asc, a.id asc
            """)
    List<Auction> findCloseTargetsForUpdate(
            @Param("statuses") Collection<AuctionStatus> statuses,
            @Param("now") LocalDateTime now,
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
}
