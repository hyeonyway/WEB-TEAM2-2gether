package com.dbidding.statistic.repository;

import com.dbidding.statistic.domain.ItemStatistic;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemStatisticRepository extends JpaRepository<ItemStatistic, Integer> {
    @Query("select s from ItemStatistic s where s.itemId in :itemIds")
    List<ItemStatistic> findAllByItemIds(@Param("itemIds") Collection<Integer> itemIds);
}
