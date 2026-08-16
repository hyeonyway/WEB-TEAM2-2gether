package com.dbidding.notification.recovery.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;

class UrgentNotificationRecoverySchedulerShedLockTest {
    @Test
    void 긴급_복구_스케줄러는_분산_락_이름과_보유_시간을_명시한다() throws Exception {
        Method method = UrgentNotificationRecoveryScheduler.class.getDeclaredMethod("recover");
        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("notification-recovery-urgent");
        assertThat(lock.lockAtLeastFor()).isEqualTo("PT10S");
        assertThat(lock.lockAtMostFor()).isEqualTo("PT2M");
    }
}
