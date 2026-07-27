package com.dbidding.card.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "item_statistics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "statistics_date"}))
public class ItemStatistic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private CardMetadata item;

    @Column(name = "statistics_date", nullable = false)
    private LocalDate statisticsDate;
    @Column(name = "latest_price")
    private Long latestPrice;
    @Column(name = "avg_price")
    private Long avgPrice;
    @Column(name = "lowest_price")
    private Long lowestPrice;
    @Column(name = "highest_price")
    private Long highestPrice;
    @Column(name = "trade_count")
    private Integer tradeCount;
    @Column(name = "bid_count")
    private Integer bidCount;
    @Column(name = "active_auction_count")
    private Integer activeAuctionCount;
    @Column(name = "daily_change_rate", precision = 8, scale = 2)
    private BigDecimal dailyChangeRate;
    @Column(name = "weekly_change_rate", precision = 8, scale = 2)
    private BigDecimal weeklyChangeRate;
    @Column(name = "monthly_change_rate", precision = 8, scale = 2)
    private BigDecimal monthlyChangeRate;

    protected ItemStatistic() {
    }

    public ItemStatistic(CardMetadata item, LocalDate statisticsDate, Long latestPrice, Long avgPrice,
                         Long lowestPrice, Long highestPrice, Integer tradeCount, Integer bidCount,
                         Integer activeAuctionCount, BigDecimal dailyChangeRate,
                         BigDecimal weeklyChangeRate, BigDecimal monthlyChangeRate) {
        this.item = item;
        this.statisticsDate = statisticsDate;
        this.latestPrice = latestPrice;
        this.avgPrice = avgPrice;
        this.lowestPrice = lowestPrice;
        this.highestPrice = highestPrice;
        this.tradeCount = tradeCount;
        this.bidCount = bidCount;
        this.activeAuctionCount = activeAuctionCount;
        this.dailyChangeRate = dailyChangeRate;
        this.weeklyChangeRate = weeklyChangeRate;
        this.monthlyChangeRate = monthlyChangeRate;
    }

    public Long getId() { return id; }
    public CardMetadata getItem() { return item; }
    public LocalDate getStatisticsDate() { return statisticsDate; }
    public Long getLatestPrice() { return latestPrice; }
    public Long getAvgPrice() { return avgPrice; }
    public Long getLowestPrice() { return lowestPrice; }
    public Long getHighestPrice() { return highestPrice; }
    public Integer getTradeCount() { return tradeCount; }
    public Integer getBidCount() { return bidCount; }
    public Integer getActiveAuctionCount() { return activeAuctionCount; }
    public BigDecimal getDailyChangeRate() { return dailyChangeRate; }
    public BigDecimal getWeeklyChangeRate() { return weeklyChangeRate; }
    public BigDecimal getMonthlyChangeRate() { return monthlyChangeRate; }
}
