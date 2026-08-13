package com.dbidding.auction.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RedisAuctionStateSeederTest {
    @Mock private AuctionRepository auctionRepository;
    @Mock private BidRepository bidRepository;
    @Mock private AuctionImageRepository auctionImageRepository;
    @Mock private RedisCardStateReader cardStateReader;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisProjectionCatchUpVerifier projectionCatchUpVerifier;
    @Mock private RedisStateSingleFlight singleFlight;
    @Mock private RedisScript<Long> auctionStateSeedScript;
    @Captor private ArgumentCaptor<Object[]> arguments;

    @Test
    void Redis_시퀀스는_입찰수_대신_마지막_반영_이벤트_버전에서_시작한다() {
        Auction auction = Auction.builder()
                .sellerId(1).itemId(2).auctionName("경매").description("설명")
                .startPrice(10_000L).deliveryFee(0L).openTime(Instant.parse("2026-08-13T00:00:00Z"))
                .estimatedCloseTime(Instant.parse("2026-08-14T00:00:00Z"))
                .closeTime(Instant.parse("2026-08-14T00:00:00Z")).bidPriceUnit(1_000L).build();
        ReflectionTestUtils.setField(auction, "id", 3000005);
        ReflectionTestUtils.setField(auction, "bidCount", 3);
        ReflectionTestUtils.setField(auction, "lastBidEventVersion", 0L);
        given(projectionCatchUpVerifier.isCaughtUp()).willReturn(true);
        given(bidRepository.findByAuctionIdInAndStatus(anyList(), any())).willReturn(List.of());
        given(cardStateReader.getCardSnapshots(anyList())).willReturn(java.util.Map.of(2,
                new CardSnapshot(2, "카드", "세트", null, null, "thumbnail")));
        given(redisTemplate.execute(eq(auctionStateSeedScript), anyList(), any(Object[].class))).willReturn(1L);

        new RedisAuctionStateSeeder(auctionRepository, bidRepository, auctionImageRepository, cardStateReader,
                redisTemplate, projectionCatchUpVerifier, singleFlight, auctionStateSeedScript)
                .seedAllIfAbsent(List.of(auction));

        verify(redisTemplate).execute(eq(auctionStateSeedScript), anyList(), arguments.capture());
        List<Object> args = List.of(arguments.getValue());
        assertThat(args.get(args.indexOf("sequence") + 1)).isEqualTo("0");
        assertThat(args.get(args.indexOf("bidCount") + 1)).isEqualTo("3");
    }
}
