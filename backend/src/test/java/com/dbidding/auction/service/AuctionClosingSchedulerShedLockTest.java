package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;

class AuctionClosingSchedulerShedLockTest {
    @Test
    void 백업_스케줄러는_분산_락_이름과_보유_시간을_명시한다() throws Exception {
        Method method = AuctionClosingScheduler.class.getDeclaredMethod("closeDueAuctions");
        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("auction-closing-backup-scheduler");
        assertThat(lock.lockAtLeastFor()).isEqualTo("PT10S");
        assertThat(lock.lockAtMostFor()).isEqualTo("PT5M");
    }
}
