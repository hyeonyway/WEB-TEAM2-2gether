package com.dbidding.card.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "item_statistics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemStatistic {
    @Id
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "item_id")
    private CardMetadata item;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;
    @Column(name = "latest_price")
    private Long latestPrice;
    @Column(name = "average_price_30d")
    private Long averagePrice30d;
    @Column(name = "lowest_price_30d")
    private Long lowestPrice30d;
    @Column(name = "highest_price_30d")
    private Long highestPrice30d;
    @Column(name = "bid_count_30d", nullable = false)
    private Integer bidCount30d;
    @Column(name = "ended_auction_count_30d", nullable = false)
    private Integer endedAuctionCount30d;
    @Column(name = "wishlist_count", nullable = false)
    private Integer wishlistCount;
    @Column(name = "daily_change_rate", precision = 8, scale = 2)
    private BigDecimal dailyChangeRate;
    @Column(name = "weekly_change_rate", precision = 8, scale = 2)
    private BigDecimal weeklyChangeRate;
    @Column(name = "monthly_change_rate", precision = 8, scale = 2)
    private BigDecimal monthlyChangeRate;

    public ItemStatistic(CardMetadata item, LocalDate asOfDate, Long latestPrice, Long averagePrice30d,
                         Long lowestPrice30d, Long highestPrice30d, Integer bidCount30d,
                         Integer endedAuctionCount30d,
                         Integer wishlistCount, BigDecimal dailyChangeRate,
                         BigDecimal weeklyChangeRate, BigDecimal monthlyChangeRate) {
        this.item = item;
        this.asOfDate = asOfDate;
        this.latestPrice = latestPrice;
        this.averagePrice30d = averagePrice30d;
        this.lowestPrice30d = lowestPrice30d;
        this.highestPrice30d = highestPrice30d;
        this.bidCount30d = bidCount30d;
        this.endedAuctionCount30d = endedAuctionCount30d;
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
