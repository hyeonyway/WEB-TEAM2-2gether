package com.dbidding.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    Optional<Order> findByAuctionId(Integer auctionId);

    List<Order> findByBuyerIdOrderByIdDesc(Integer buyerId);

    List<Order> findBySellerIdOrderByIdDesc(Integer sellerId);
}
