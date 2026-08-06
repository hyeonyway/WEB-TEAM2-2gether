package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.dbidding.auction.repository.AuctionInsightQueryRepository;
import org.junit.jupiter.api.Test;

class AuctionInsightQueryServiceTest {
    private final AuctionInsightQueryRepository repository = mock(AuctionInsightQueryRepository.class);
    private final AuctionInsightQueryService service = new AuctionInsightQueryService(repository);

    @Test
    void 진행_경매_집계를_경매_조회_DTO로_제공한다() {
        var aggregate = mock(AuctionInsightQueryRepository.OpenAuctionInsightAggregate.class);
        given(aggregate.getTotalCount()).willReturn(11L);
        given(aggregate.getRisingCount()).willReturn(3L);
        given(aggregate.getAverageRisingRate()).willReturn(12.345);
        given(aggregate.getBidAuctionCount()).willReturn(7L);
        given(repository.aggregateOpenAuctionInsight()).willReturn(aggregate);

        var insight = service.getOpenAuctionInsight();

        assertThat(insight).isEqualTo(new AuctionInsightQueryService.AuctionInsight(11L, 3L, 12.345, 7L));
    }
}
