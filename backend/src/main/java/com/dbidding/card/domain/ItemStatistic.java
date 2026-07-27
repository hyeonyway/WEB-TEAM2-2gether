package com.dbidding.card.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "item_statistics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "statistics_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemStatistic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private CardMetadata item;

    @Column(name = "statistics_date", nullable = false)
    private LocalDateTime statisticsDate;
    @Column(name = "latest_price")
    private Long latestPrice;
    @Column(name = "avg_price")
    private Long avgPrice;
    @Column(name = "lowest_price")
    private Long lowestPrice;
    @Column(name = "highest_price")
    private Long highestPrice;
    @Column(name = "bid_count")
    private Integer bidCount;
    @Column(name = "active_auction_count")
    private Integer activeAuctionCount;
    @Column(name = "wishlist_count", nullable = false)
    private Integer wishlistCount;
    @Column(name = "daily_change_rate", precision = 8, scale = 2)
    private BigDecimal dailyChangeRate;
    @Column(name = "weekly_change_rate", precision = 8, scale = 2)
    private BigDecimal weeklyChangeRate;
    @Column(name = "monthly_change_rate", precision = 8, scale = 2)
    private BigDecimal monthlyChangeRate;

    public ItemStatistic(CardMetadata item, LocalDateTime statisticsDate, Long latestPrice, Long avgPrice,
                         Long lowestPrice, Long highestPrice, Integer bidCount,
                         Integer activeAuctionCount, Integer wishlistCount, BigDecimal dailyChangeRate,
                         BigDecimal weeklyChangeRate, BigDecimal monthlyChangeRate) {
        this.item = item;
        this.statisticsDate = statisticsDate;
        this.latestPrice = latestPrice;
        this.avgPrice = avgPrice;
        this.lowestPrice = lowestPrice;
        this.highestPrice = highestPrice;
        this.bidCount = bidCount;
        this.activeAuctionCount = activeAuctionCount;
        this.wishlistCount = wishlistCount;
        this.dailyChangeRate = dailyChangeRate;
        this.weeklyChangeRate = weeklyChangeRate;
        this.monthlyChangeRate = monthlyChangeRate;
    }

    public void updateChangeRates(
            BigDecimal dailyChangeRate,
            BigDecimal weeklyChangeRate,
            BigDecimal monthlyChangeRate
    ) {
        this.dailyChangeRate = dailyChangeRate;
        this.weeklyChangeRate = weeklyChangeRate;
        this.monthlyChangeRate = monthlyChangeRate;
    }
}
