package com.dbidding.statistics.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "item_daily_statistics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "statistics_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemDailyStatistic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Integer itemId;

    @Column(name = "statistics_date", nullable = false)
    private LocalDate statisticsDate;
    private Long latestPrice;
    private Long averagePrice;
    private Long lowestPrice;
    private Long highestPrice;
    private Integer bidCount;
    private Integer endedAuctionCount;

    public ItemDailyStatistic(Integer itemId, LocalDate statisticsDate, Long latestPrice,
                              Long averagePrice, Long lowestPrice, Long highestPrice,
                              Integer bidCount, Integer endedAuctionCount) {
        this.itemId = itemId;
        this.statisticsDate = statisticsDate;
        this.latestPrice = latestPrice;
        this.averagePrice = averagePrice;
        this.lowestPrice = lowestPrice;
        this.highestPrice = highestPrice;
        this.bidCount = bidCount;
        this.endedAuctionCount = endedAuctionCount;
    }
}
