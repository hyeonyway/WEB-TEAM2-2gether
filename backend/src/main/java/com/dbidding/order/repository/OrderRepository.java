package com.dbidding.order.repository;

import com.dbidding.order.domain.Order;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    Optional<Order> findByAuctionId(Integer auctionId);

    List<Order> findByBuyerIdOrderByIdDesc(Integer buyerId);

    List<Order> findBySellerIdOrderByIdDesc(Integer sellerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Integer id);
}
