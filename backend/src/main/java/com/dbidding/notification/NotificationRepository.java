package com.dbidding.notification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByUserIdAndAuctionIdAndTypeAndBidId(Integer userId, Integer auctionId, NotificationType type, Long bidId);

    Optional<Notification> findByUserIdAndAuctionIdAndTypeAndBidId(
            Integer userId, Integer auctionId, NotificationType type, Long bidId
    );

    List<Notification> findByUserIdOrderByIdDesc(Integer userId, Pageable pageable);

    List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(Integer userId, Long cursor, Pageable pageable);

    List<Notification> findByUserIdAndIsReadFalseOrderByIdDesc(Integer userId, Pageable pageable);

    List<Notification> findByUserIdAndIsReadFalseAndIdLessThanOrderByIdDesc(Integer userId, Long cursor, Pageable pageable);

    long countByUserIdAndIsReadFalse(Integer userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Integer userId);
}
