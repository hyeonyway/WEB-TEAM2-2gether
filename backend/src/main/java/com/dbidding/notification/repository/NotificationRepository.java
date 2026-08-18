package com.dbidding.notification.repository;

import com.dbidding.notification.domain.Notification;
import com.dbidding.notification.domain.NotificationType;
import java.util.Collection;
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

    /**
     * 낙찰/유찰 복구 알림(bidId=NO_BID 고정)의 존재 여부를 경매·유저 여러 건에 대해 한 번에 확인하기 위한 조회.
     * auctionIds/userIds는 독립적인 IN절이라 실제 조합보다 넓게 매칭될 수 있지만, 반환되는 각 행은
     * 실제 존재하는 알림이므로 행 자체의 (userId, auctionId, type)으로 키를 만들면 오탐 없이 안전하다.
     */
    List<Notification> findByBidIdAndAuctionIdInAndUserIdIn(
            Long bidId, Collection<Integer> auctionIds, Collection<Integer> userIds
    );

    /**
     * 상회입찰 복구 알림의 존재 여부를 여러 bid에 대해 한 번에 확인하기 위한 조회.
     * bidId가 sentinel(0)이 아닌 경우는 설계상 OUTBID뿐이라(Notification 클래스 주석 참고)
     * type 조건 없이 bidId만으로 충분하다.
     */
    List<Notification> findByBidIdIn(Collection<Long> bidIds);

    List<Notification> findByUserIdOrderByIdDesc(Integer userId, Pageable pageable);

    List<Notification> findByUserIdAndIdLessThanOrderByIdDesc(Integer userId, Long cursor, Pageable pageable);

    List<Notification> findByUserIdAndIsReadFalseOrderByIdDesc(Integer userId, Pageable pageable);

    List<Notification> findByUserIdAndIsReadFalseAndIdLessThanOrderByIdDesc(Integer userId, Long cursor, Pageable pageable);

    long countByUserIdAndIsReadFalse(Integer userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Integer userId);
}
