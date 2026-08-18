package com.dbidding.auction.service.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;

class RedisAuctionActiveIndexCleanupSchedulerShedLockTest {
    @Test
    void GC_스케줄러는_분산_락_이름과_보유_시간을_명시한다() throws Exception {
        Method method = RedisAuctionActiveIndexCleanupScheduler.class.getDeclaredMethod("removeTerminalEntries");
        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("auction-active-index-cleanup");
        assertThat(lock.lockAtLeastFor()).isEqualTo("PT10S");
        assertThat(lock.lockAtMostFor()).isEqualTo("PT10M");
    }
}
