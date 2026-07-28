package com.dbidding.card.repository;

import com.dbidding.card.domain.ItemDailyStatistic;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemDailyStatisticRepository extends JpaRepository<ItemDailyStatistic, Long> {
    List<ItemDailyStatistic> findByItemIdAndStatisticsDateGreaterThanEqualAndStatisticsDateLessThanOrderByStatisticsDate(
            Integer itemId, LocalDate from, LocalDate to);

    Optional<ItemDailyStatistic> findFirstByItemIdAndStatisticsDateLessThanOrderByStatisticsDateDesc(
            Integer itemId, LocalDate before);

    @Query("""
            select s from ItemDailyStatistic s
            join fetch s.item
            where s.item.id in :itemIds
              and s.statisticsDate >= :from
              and s.statisticsDate < :to
            order by s.item.id, s.statisticsDate
            """)
    List<ItemDailyStatistic> findHistory(
            @Param("itemIds") Collection<Integer> itemIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select s from ItemDailyStatistic s
            join fetch s.item
            where s.statisticsDate = :date
            """)
    List<ItemDailyStatistic> findAllWithItemByStatisticsDate(@Param("date") LocalDate date);
}
