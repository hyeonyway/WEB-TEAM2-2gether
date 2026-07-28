package com.dbidding.card.repository;

import com.dbidding.card.domain.ItemStatistic;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemStatisticRepository extends JpaRepository<ItemStatistic, Integer> {
    @Query("select s from ItemStatistic s join fetch s.item where s.item.id in :itemIds")
    List<ItemStatistic> findAllByItemIds(@Param("itemIds") Collection<Integer> itemIds);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            insert into item_statistics (
                item_id, as_of_date, bid_count_30d, ended_auction_count_30d,
                active_auction_count, wishlist_count
            ) values (
                :itemId, date_sub(current_date, interval 1 day), 0, 0,
                (select count(*) from auctions
                 where item_id = :itemId and status in ('OPEN', 'ENDING')),
                0
            )
            on duplicate key update
                active_auction_count = (
                    select count(*) from auctions
                    where item_id = :itemId and status in ('OPEN', 'ENDING')
                )
            """, nativeQuery = true)
    void refreshActiveAuctionCount(@Param("itemId") Integer itemId);
}
