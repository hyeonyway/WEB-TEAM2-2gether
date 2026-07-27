package com.dbidding.card.repository;

import com.dbidding.card.domain.ItemStatistic;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemStatisticRepository extends JpaRepository<ItemStatistic, Long> {
    Optional<ItemStatistic> findFirstByItemIdOrderByStatisticsDateDesc(Long itemId);

    Optional<ItemStatistic> findByItemIdAndStatisticsDate(Long itemId, LocalDate statisticsDate);

    Optional<ItemStatistic> findFirstByItemIdAndStatisticsDateLessThanEqualOrderByStatisticsDateDesc(
            Long itemId, LocalDate statisticsDate);

    List<ItemStatistic> findByItemIdAndStatisticsDateGreaterThanEqualOrderByStatisticsDate(
            Long itemId, LocalDate from);

    @Query("""
            select s from ItemStatistic s
            where s.item.id in :itemIds
              and s.statisticsDate = (
                select max(latest.statisticsDate) from ItemStatistic latest
                where latest.item.id = s.item.id
              )
            """)
    List<ItemStatistic> findLatestByItemIds(@Param("itemIds") List<Long> itemIds);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            insert into item_statistics (
                item_id, statistics_date, trade_amount_sum, trade_count,
                bid_count, active_auction_count
            ) values (:itemId, :date, 0, 0, 1, 0)
            on duplicate key update
                bid_count = coalesce(bid_count, 0) + 1,
                calculated_at = current_timestamp(6)
            """, nativeQuery = true)
    void incrementBidCount(@Param("itemId") Long itemId, @Param("date") LocalDate date);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            insert into item_statistics (
                item_id, statistics_date, trade_amount_sum, trade_count,
                bid_count, active_auction_count
            ) values (
                :itemId, :date, 0, 0, 0,
                (select count(*) from auctions
                 where item_id = :itemId and status = 'OPEN')
            )
            on duplicate key update
                active_auction_count = (
                    select count(*) from auctions
                    where item_id = :itemId and status = 'OPEN'
                ),
                calculated_at = current_timestamp(6)
            """, nativeQuery = true)
    void refreshActiveAuctionCount(@Param("itemId") Long itemId, @Param("date") LocalDate date);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            insert into item_statistics (
                item_id, statistics_date, latest_price, avg_price,
                lowest_price, highest_price, trade_amount_sum, trade_count,
                bid_count, active_auction_count
            ) values (
                :itemId, :date, :winningPrice, :winningPrice,
                :winningPrice, :winningPrice, :winningPrice, 1, 0, 0
            )
            on duplicate key update
                avg_price = round(
                    (coalesce(trade_amount_sum, 0) + :winningPrice)
                    / (coalesce(trade_count, 0) + 1)
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
                trade_amount_sum = coalesce(trade_amount_sum, 0) + :winningPrice,
                trade_count = coalesce(trade_count, 0) + 1,
                calculated_at = current_timestamp(6)
            """, nativeQuery = true)
    void recordCompletedAuction(@Param("itemId") Long itemId,
                                @Param("date") LocalDate date,
                                @Param("winningPrice") Long winningPrice);
}
