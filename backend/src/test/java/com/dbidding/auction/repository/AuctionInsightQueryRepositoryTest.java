package com.dbidding.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class AuctionInsightQueryRepositoryTest {

    @Test
    void 진행_경매_인사이트는_종료_예정_시각이_지나지_않은_OPEN_경매만_집계한다() throws NoSuchMethodException {
        Method method = AuctionInsightQueryRepository.class.getMethod("aggregateOpenAuctionInsight");
        String query = method.getAnnotation(Query.class).value();

        assertThat(query)
                .contains("a.status = com.dbidding.auction.domain.AuctionStatus.OPEN")
                .contains("a.estimatedCloseTime > CURRENT_TIMESTAMP")
                .doesNotContain("AuctionStatus.ENDING");
    }
}
