package com.dbidding.auction.sse;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
@Validated
public class AuctionSseController {
    private final AuctionSseConnectionManager connectionManager;

    /**
     * 목록 화면은 스크롤 가시영역 기준 최대 15개까지 구독한다. 여기에 입찰 모달이 뜬
     * 경매 1개가 화면 밖으로 스크롤돼도 별도로 계속 구독되므로(프론트 useAuctionStream이
     * 컴포넌트별로 독립 구독해 합집합으로 연결하는 구조) 최악의 경우 15 + 1 = 16개까지
     * 늘어날 수 있다. 그래서 캡을 16으로 둔다.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam @NotEmpty @Size(max = 16) Set<@Positive Integer> auctionIds,
            HttpServletResponse response
    ) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return connectionManager.connect(auctionIds);
    }
}
