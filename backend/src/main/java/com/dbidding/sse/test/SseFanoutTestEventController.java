package com.dbidding.sse.test;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 순수 SSE fan-out 부하테스트 전용 엔드포인트(#569) — 실제 입찰 처리 없이 auction/notification/
 * wallet 3채널을 실제 Redis publish 경로로 동시에 발행한다. 어떤 유저가 어떤 경매를 담당하는지는
 * 부하테스트 스크립트가 알고 있으므로 요청 파라미터로 그대로 받는다.
 */
@RestController
@Profile("test")
@RequestMapping("/api/test/sse-fanout")
@RequiredArgsConstructor
public class SseFanoutTestEventController {
    private final SseFanoutTestEventService fanoutService;

    @PostMapping("/random-bid-event")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SseFanoutTestEventResult publishRandomBidEvent(
            @RequestParam Integer auctionId,
            @RequestParam Integer outbidUserId,
            @RequestParam Integer newBidderUserId
    ) {
        return fanoutService.publishRandomBidEvent(auctionId, outbidUserId, newBidderUserId);
    }
}
