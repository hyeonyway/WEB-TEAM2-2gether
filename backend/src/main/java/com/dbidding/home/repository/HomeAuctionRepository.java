package com.dbidding.home.repository;

import com.dbidding.auction.domain.Auction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface HomeAuctionRepository extends Repository<Auction, Integer> {
    interface InsightAggregate {
        Long getTotalCount();
        Long getRisingCount();
        Double getAverageRisingRate();
        Long getBidAuctionCount();
    }

    interface DailyMarketAggregate {
        LocalDate getAuctionDate();
        Double getAveragePrice();
        Long getBidCount();
    }

    @Query("""
            select count(a) as totalCount,
                   sum(case when a.startPrice > 0 and a.currentPrice > a.startPrice then 1 else 0 end) as risingCount,
                   avg(case when a.startPrice > 0 and a.currentPrice > a.startPrice
                       then (a.currentPrice - a.startPrice) * 100.0 / a.startPrice
                       else null end) as averageRisingRate,
                   sum(case when a.bidCount > 0 then 1 else 0 end) as bidAuctionCount
            from Auction a
            where a.status in (com.dbidding.auction.domain.AuctionStatus.OPEN,
                               com.dbidding.auction.domain.AuctionStatus.ENDING)
            """)
    InsightAggregate aggregateInsights();

    @Query(value = """
            select date(close_time) as auctionDate,
                   avg(current_price) as averagePrice,
                   sum(bid_count) as bidCount
            from auctions
            where status = 'ENDED'
              and close_time >= :from
              and close_time < :to
            group by date(close_time)
            order by date(close_time)
            """, nativeQuery = true)
    List<DailyMarketAggregate> aggregateDailyMarket(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query(value = """
            select avg(current_price)
            from auctions
            where status = 'ENDED'
              and date(close_time) = (
                  select max(date(previous.close_time))
                  from auctions previous
                  where previous.status = 'ENDED'
                    and previous.close_time < :before
              )
            """, nativeQuery = true)
    Optional<Double> findPreviousDailyAverage(@Param("before") LocalDateTime before);
}
