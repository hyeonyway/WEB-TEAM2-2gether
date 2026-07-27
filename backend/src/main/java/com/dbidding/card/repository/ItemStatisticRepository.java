package com.dbidding.card.repository;

import com.dbidding.card.domain.ItemStatistic;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemStatisticRepository extends JpaRepository<ItemStatistic, Long> {
    Optional<ItemStatistic> findFirstByItemIdOrderByStatisticsDateDesc(Long itemId);

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
}
