package com.dbidding.card.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface StatisticAggregationRepository extends Repository<com.dbidding.card.domain.ItemStatistic, Integer> {
    @Modifying
    @Query(value = """
            insert into item_daily_statistics (
                item_id, statistics_date, latest_price, average_price,
                lowest_price, highest_price, bid_count, ended_auction_count
            )
            select a.item_id, :date,
                   substring_index(group_concat(a.current_price order by a.close_time desc, a.id desc), ',', 1) + 0,
                   round(avg(a.current_price)), min(a.current_price), max(a.current_price),
                   sum((select count(*) from bids b where b.auction_id = a.id)), count(*)
            from auctions a
            where a.status = 'ENDED' and a.close_time >= :from and a.close_time < :to
            group by a.item_id
            on duplicate key update
                latest_price = values(latest_price), average_price = values(average_price),
                lowest_price = values(lowest_price), highest_price = values(highest_price),
                bid_count = values(bid_count), ended_auction_count = values(ended_auction_count)
            """, nativeQuery = true)
    void aggregateItems(@Param("date") LocalDate date,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to);

    @Modifying
    @Query(value = """
            insert into market_daily_statistics (
                statistics_date, average_price, lowest_price, highest_price, winning_price_total_30d,
                highest_price_30d, bid_count_30d,
                ended_auction_count_30d, bid_count, ended_auction_count
            )
            select :date,
                   (select round(avg(a.current_price)) from auctions a
                    where a.status = 'ENDED' and a.close_time >= :from and a.close_time < :to),
                   (select min(a.current_price) from auctions a
                    where a.status = 'ENDED' and a.close_time >= :from and a.close_time < :to),
                   (select max(a.current_price) from auctions a
                    where a.status = 'ENDED' and a.close_time >= :from and a.close_time < :to),
                   (select coalesce(sum(a.current_price), 0) from auctions a
                    where a.status = 'ENDED'
                      and a.close_time >= date_sub(:from, interval 29 day)
                      and a.close_time < :to),
                   (select coalesce(max(a.current_price), 0) from auctions a
                    where a.status = 'ENDED'
                      and a.close_time >= date_sub(:from, interval 29 day)
                      and a.close_time < :to),
                   (select count(*) from bids b join auctions a on a.id = b.auction_id
                    where a.status = 'ENDED'
                      and a.close_time >= date_sub(:from, interval 29 day)
                      and a.close_time < :to),
                   (select count(*) from auctions a
                    where a.status = 'ENDED'
                      and a.close_time >= date_sub(:from, interval 29 day)
                      and a.close_time < :to),
                   (select count(*) from bids b join auctions a on a.id = b.auction_id
                    where a.status = 'ENDED' and a.close_time >= :from and a.close_time < :to),
                   (select count(*) from auctions a
                    where a.status = 'ENDED' and a.close_time >= :from and a.close_time < :to)
            on duplicate key update
                average_price = values(average_price), lowest_price = values(lowest_price),
                highest_price = values(highest_price),
                winning_price_total_30d = values(winning_price_total_30d),
                highest_price_30d = values(highest_price_30d),
                bid_count_30d = values(bid_count_30d),
                ended_auction_count_30d = values(ended_auction_count_30d),
                bid_count = values(bid_count),
                ended_auction_count = values(ended_auction_count)
            """, nativeQuery = true)
    void aggregateMarket(@Param("date") LocalDate date,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);

    @Modifying
    @Query(value = """
            insert into item_statistics (
                item_id, as_of_date, latest_price, average_price_30d,
                lowest_price_30d, highest_price_30d, bid_count_30d,
                ended_auction_count_30d, wishlist_count,
                daily_change_rate, weekly_change_rate, monthly_change_rate
            )
            select c.id, :asOf,
                   (select d.latest_price from item_daily_statistics d
                    where d.item_id = c.id and d.statistics_date <= :asOf
                    order by d.statistics_date desc limit 1),
                   (select round(sum(d.average_price * d.ended_auction_count)
                                      / nullif(sum(d.ended_auction_count), 0))
                    from item_daily_statistics d
                    where d.item_id = c.id and d.statistics_date >= :from
                      and d.statistics_date <= :asOf),
                   (select min(d.lowest_price) from item_daily_statistics d
                    where d.item_id = c.id and d.statistics_date >= :from
                      and d.statistics_date <= :asOf),
                   (select max(d.highest_price) from item_daily_statistics d
                    where d.item_id = c.id and d.statistics_date >= :from
                      and d.statistics_date <= :asOf),
                   (select coalesce(sum(d.bid_count), 0) from item_daily_statistics d
                    where d.item_id = c.id and d.statistics_date >= :from
                      and d.statistics_date <= :asOf),
                   (select coalesce(sum(d.ended_auction_count), 0) from item_daily_statistics d
                    where d.item_id = c.id and d.statistics_date >= :from
                      and d.statistics_date <= :asOf),
                   coalesce((select old.wishlist_count from item_statistics old
                             where old.item_id = c.id), 0),
                   0.00, 0.00, 0.00
            from card_metadata c
            on duplicate key update
                as_of_date = values(as_of_date), latest_price = values(latest_price),
                average_price_30d = values(average_price_30d),
                lowest_price_30d = values(lowest_price_30d),
                highest_price_30d = values(highest_price_30d),
                bid_count_30d = values(bid_count_30d),
                ended_auction_count_30d = values(ended_auction_count_30d)
            """, nativeQuery = true)
    void refreshRollingSnapshots(@Param("from") LocalDate from, @Param("asOf") LocalDate asOf);

    @Modifying
    @Query(value = """
            update item_statistics s
            set daily_change_rate = coalesce(round(
                    (s.latest_price - (select d.latest_price from item_daily_statistics d
                     where d.item_id = s.item_id and d.statistics_date <= :dailyBase
                     order by d.statistics_date desc limit 1)) * 100.0
                    / nullif((select d.latest_price from item_daily_statistics d
                              where d.item_id = s.item_id and d.statistics_date <= :dailyBase
                              order by d.statistics_date desc limit 1), 0), 2), 0.00),
                weekly_change_rate = coalesce(round(
                    (s.latest_price - (select d.latest_price from item_daily_statistics d
                     where d.item_id = s.item_id and d.statistics_date <= :weeklyBase
                     order by d.statistics_date desc limit 1)) * 100.0
                    / nullif((select d.latest_price from item_daily_statistics d
                              where d.item_id = s.item_id and d.statistics_date <= :weeklyBase
                              order by d.statistics_date desc limit 1), 0), 2), 0.00),
                monthly_change_rate = coalesce(round(
                    (s.latest_price - (select d.latest_price from item_daily_statistics d
                     where d.item_id = s.item_id and d.statistics_date <= :monthlyBase
                     order by d.statistics_date desc limit 1)) * 100.0
                    / nullif((select d.latest_price from item_daily_statistics d
                              where d.item_id = s.item_id and d.statistics_date <= :monthlyBase
                              order by d.statistics_date desc limit 1), 0), 2), 0.00)
            """, nativeQuery = true)
    void refreshChangeRates(@Param("dailyBase") LocalDate dailyBase,
                            @Param("weeklyBase") LocalDate weeklyBase,
                            @Param("monthlyBase") LocalDate monthlyBase);
}
