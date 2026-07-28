package com.dbidding.card.repository;

import com.dbidding.card.domain.ItemStatistic;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemStatisticRepository extends JpaRepository<ItemStatistic, Integer> {
    Optional<ItemStatistic> findFirstByItemIdOrderByStatisticsDateDesc(Integer itemId);

    Optional<ItemStatistic> findByItemIdAndStatisticsDate(Integer itemId, LocalDateTime statisticsDate);

    Optional<ItemStatistic> findFirstByItemIdAndStatisticsDateLessThanEqualOrderByStatisticsDateDesc(
            Integer itemId, LocalDateTime statisticsDate);

    List<ItemStatistic> findByItemIdAndStatisticsDateGreaterThanEqualOrderByStatisticsDate(
            Integer itemId, LocalDateTime from);

    @Query("""
            select s from ItemStatistic s
            join fetch s.item
            where s.statisticsDate = (
                select max(latest.statisticsDate) from ItemStatistic latest
                where latest.item.id = s.item.id
                  and latest.statisticsDate < :cutoff
            )
            """)
    List<ItemStatistic> findLatestForEveryItemBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
            select s from ItemStatistic s
            where s.item.id in :itemIds
              and s.statisticsDate = (
                select max(latest.statisticsDate) from ItemStatistic latest
                where latest.item.id = s.item.id
              )
            """)
    List<ItemStatistic> findLatestByItemIds(@Param("itemIds") List<Integer> itemIds);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            insert into item_statistics (
                item_id, statistics_date, bid_count, active_auction_count
            ) values (:itemId, :date, 1, 0)
            on duplicate key update
                bid_count = coalesce(bid_count, 0) + 1
            """, nativeQuery = true)
    void incrementBidCount(@Param("itemId") Integer itemId, @Param("date") LocalDateTime date);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            insert into item_statistics (
                item_id, statistics_date, bid_count, active_auction_count
            ) values (
                :itemId, :date, 0,
                (select count(*) from auctions
                 where item_id = :itemId and status = 'OPEN')
            )
            on duplicate key update
                active_auction_count = (
                    select count(*) from auctions
                    where item_id = :itemId and status = 'OPEN'
                )
            """, nativeQuery = true)
    void refreshActiveAuctionCount(@Param("itemId") Integer itemId, @Param("date") LocalDateTime date);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            insert into item_statistics (
                item_id, statistics_date, latest_price, avg_price,
                lowest_price, highest_price,
                bid_count, active_auction_count
            ) values (
                :itemId, :date, :winningPrice, :winningPrice,
                :winningPrice, :winningPrice, 0, 0
            )
            on duplicate key update
                avg_price = coalesce(
                    (select round(avg(current_price)) from auctions
                     where item_id = :itemId and status = 'ENDED'),
                    :winningPrice
                ),
                latest_price = :winningPrice,
                lowest_price = case
                    when lowest_price is null then :winningPrice
                    else least(lowest_price, :winningPrice)
                end,
                highest_price = case
                    when highest_price is null then :winningPrice
                    else greatest(highest_price, :winningPrice)
                end,
                bid_count = coalesce(bid_count, 0)
            """, nativeQuery = true)
    void recordCompletedAuction(@Param("itemId") Integer itemId,
                                @Param("date") LocalDateTime date,
                                @Param("winningPrice") Long winningPrice);
}
