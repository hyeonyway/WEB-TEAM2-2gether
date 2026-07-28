package com.dbidding.auction.repository;

import com.dbidding.auction.domain.AuctionImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionImageRepository extends JpaRepository<AuctionImage, Integer> {
    List<AuctionImage> findByAuctionIdOrderById(Integer auctionId);

    List<AuctionImage> findByAuctionIdInOrderById(Collection<Integer> auctionIds);
}
