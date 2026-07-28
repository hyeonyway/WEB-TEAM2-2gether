package com.dbidding.home.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    private Integer bidCount;
    private Integer endedAuctionCount;
}
