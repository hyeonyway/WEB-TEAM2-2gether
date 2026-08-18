package com.dbidding.notification.sse;

import com.dbidding.notification.Notification;
import com.dbidding.notification.NotificationType;
import com.dbidding.notification.dto.NotificationResponse;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 실제 낙찰/아웃비드 처리 없이 notification SSE fan-out만 재현하는 테스트 전용 발행자(#569).
 * 실제 {@link NotificationPushPublisher} 빈(Redis 경로)을 그대로 태워, publish→subscribe→push
 * 전체 경로의 fan-out 비용을 측정할 수 있게 한다.
 */
@Service
@Profile("test")
@RequiredArgsConstructor
public class NotificationSseTestPushService {
    private final NotificationPushPublisher pushPublisher;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();

    public NotificationResponse publishTestPush(Integer userId, Integer auctionId) {
        var payload = new NotificationResponse(
                sequence.incrementAndGet(), auctionId, NotificationType.OUTBID, Notification.NO_BID,
                "[부하테스트] 경매 " + auctionId + "번에 새 입찰이 등록되었습니다.", false, clock.instant());
        pushPublisher.publish(userId, payload);
        return payload;
    }
}
