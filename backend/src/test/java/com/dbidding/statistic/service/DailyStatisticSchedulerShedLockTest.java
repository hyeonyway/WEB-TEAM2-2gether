package com.dbidding.statistic.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.global.config.ShedLockConfig;
import java.lang.reflect.Method;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class DailyStatisticSchedulerShedLockTest {
    @Test
    void 기동_캐치업과_00시10분_스케줄이_동일한_분산_락을_사용한다() throws Exception {
        SchedulerLock startupLock = schedulerMethod("aggregateOnStartup").getAnnotation(SchedulerLock.class);
        Method scheduledMethod = schedulerMethod("aggregateOnSchedule");
        SchedulerLock scheduledLock = scheduledMethod.getAnnotation(SchedulerLock.class);

        assertThat(startupLock.name()).isEqualTo("daily-statistic-aggregation");
        assertThat(startupLock.lockAtLeastFor()).isEqualTo("PT1M");
        assertThat(startupLock.lockAtMostFor()).isEqualTo("PT30M");
        assertThat(scheduledLock.name()).isEqualTo(startupLock.name());
        assertThat(scheduledLock.lockAtLeastFor()).isEqualTo(startupLock.lockAtLeastFor());
        assertThat(scheduledLock.lockAtMostFor()).isEqualTo(startupLock.lockAtMostFor());
        assertThat(scheduledMethod.getAnnotation(Scheduled.class).cron()).isEqualTo("0 10 0 * * *");
        assertThat(scheduledMethod.getAnnotation(Scheduled.class).zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void MySQL_시간을_기준으로_하는_Jdbc_락_프로바이더를_구성한다() {
        LockProvider lockProvider = new ShedLockConfig().lockProvider(org.mockito.Mockito.mock(DataSource.class));

        assertThat(ShedLockConfig.class.isAnnotationPresent(EnableSchedulerLock.class)).isTrue();
        assertThat(lockProvider).isInstanceOf(JdbcTemplateLockProvider.class);
    }

    private Method schedulerMethod(String name) throws NoSuchMethodException {
        return DailyStatisticScheduler.class.getMethod(name);
    }
}
