package com.dbidding.home.repository;

import com.dbidding.auction.domain.Auction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface HomeAuctionRepository extends Repository<Auction, Integer> {
    interface InsightAggregate {
        Long getTotalCount();
        Long getRisingCount();
        Double getAverageRisingRate();
        Long getBidAuctionCount();
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

}
