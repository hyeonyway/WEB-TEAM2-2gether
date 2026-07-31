package com.dbidding.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class StatisticAuctionQueryRepositoryTest {

    @Test
    void 홈_인사이트는_OPEN_상태_경매만_집계한다() throws NoSuchMethodException {
        Method method = StatisticAuctionQueryRepository.class.getMethod("aggregateInsights");
        String query = method.getAnnotation(Query.class).value();

        assertThat(query)
                .contains("a.status = com.dbidding.auction.domain.AuctionStatus.OPEN")
                .doesNotContain("AuctionStatus.ENDING");
    }
}
