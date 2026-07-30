package com.dbidding.notification;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
