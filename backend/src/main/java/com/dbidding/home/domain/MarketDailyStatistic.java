package com.dbidding.home.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "market_daily_statistics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketDailyStatistic {
    @Id
    private LocalDate statisticsDate;
    private Long averagePrice;
    private Long lowestPrice;
    private Long highestPrice;
    @Column(name = "winning_price_total_30d")
    private Long winningPriceTotal30d;
    @Column(name = "highest_price_30d")
    private Long highestPrice30d;
    @Column(name = "bid_count_30d")
    private Integer bidCount30d;
    @Column(name = "ended_auction_count_30d")
    private Integer endedAuctionCount30d;
    private Integer bidCount;
    private Integer endedAuctionCount;
}
