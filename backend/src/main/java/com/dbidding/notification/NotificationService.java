package com.dbidding.notification;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification save(Integer userId, Integer auctionId, String message) {
        return notificationRepository.save(Notification.of(userId, auctionId, message));
    }

    public List<Notification> findAll(Integer userId) {
        return notificationRepository.findByUserIdOrderByIdDesc(userId);
    }

    public List<Notification> findUnread(Integer userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByIdDesc(userId);
    }

    @Transactional
    public void markAsRead(Integer userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다."));
        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(Integer userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }
}
