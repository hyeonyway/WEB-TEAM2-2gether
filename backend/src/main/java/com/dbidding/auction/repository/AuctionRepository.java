package com.dbidding.auction.repository;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionRepository extends JpaRepository<Auction, Integer> {
    @Query("""
            select a from Auction a
            where (:status is null or a.status = :status)
              and (:keyword = '' or lower(a.auctionName) like lower(concat('%', :keyword, '%')))
              and (:psaGrade is null or a.itemId in (
                    select c.id from CardMetadata c where c.psaGrade = :psaGrade
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
            @Param("status") AuctionStatus status,
            @Param("sort") String sort,
            Pageable pageable
    );

    Optional<Auction> findByIdAndStatusNot(Integer id, AuctionStatus status);

    long countByItemIdAndStatusIn(Integer itemId, Collection<AuctionStatus> statuses);
}
