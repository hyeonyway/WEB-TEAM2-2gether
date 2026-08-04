package com.dbidding.statistic.repository;

import com.dbidding.statistic.domain.ItemDailyStatistic;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemDailyStatisticRepository extends JpaRepository<ItemDailyStatistic, Long> {
    List<ItemDailyStatistic> findByItemIdAndStatisticsDateGreaterThanEqualAndStatisticsDateLessThanOrderByStatisticsDate(
            Integer itemId, LocalDate from, LocalDate to);

    @Query("""
            select s from ItemDailyStatistic s
            where s.itemId in :itemIds
              and s.statisticsDate >= :from
              and s.statisticsDate < :to
            order by s.itemId, s.statisticsDate
            """)
    List<ItemDailyStatistic> findHistory(
            @Param("itemIds") Collection<Integer> itemIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(value = """
            with ranked_prices as (
                select s.item_id,
                       s.statistics_date,
                       s.latest_price as price,
                       s.bid_count,
                       row_number() over (
                           partition by s.item_id
                           order by s.statistics_date desc, s.id desc
                       ) as price_rank
                from item_daily_statistics s
                where s.statistics_date >= :from
                  and s.statistics_date < :to
                  and s.latest_price is not null
                  and s.latest_price > 0
            )
            select current_price.item_id as cardId,
                   current_price.statistics_date as currentDate,
                   current_price.price as currentPrice,
                   current_price.bid_count as bidCount,
                   previous_price.statistics_date as previousDate,
                   previous_price.price as previousPrice
            from ranked_prices current_price
            join ranked_prices previous_price
              on previous_price.item_id = current_price.item_id
             and previous_price.price_rank = 2
            where current_price.price_rank = 1
            """, nativeQuery = true)
    List<PriceMovementCandidate> findPriceMovementCandidates(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
