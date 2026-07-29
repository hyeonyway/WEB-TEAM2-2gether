package com.dbidding.card.repository;

import com.dbidding.card.domain.ItemStatistic;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemStatisticRepository extends JpaRepository<ItemStatistic, Integer> {
    @Query("select s from ItemStatistic s join fetch s.item where s.item.id in :itemIds")
    List<ItemStatistic> findAllByItemIds(@Param("itemIds") Collection<Integer> itemIds);
}
