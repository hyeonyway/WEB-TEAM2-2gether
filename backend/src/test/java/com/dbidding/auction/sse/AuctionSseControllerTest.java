package com.dbidding.auction.sse;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(AuctionSseController.class)
class AuctionSseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuctionSseConnectionManager connectionManager;

    @Test
    void 스트림_응답은_프록시_버퍼링과_캐시를_비활성화한다() throws Exception {
        SseEmitter emitter = new SseEmitter();
        given(connectionManager.connect(null)).willReturn(emitter);

        mockMvc.perform(get("/api/auctions/stream"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(header().string("X-Accel-Buffering", "no"));

        verify(connectionManager).connect(null);
    }

    @Test
    void 마지막_수신_이벤트_ID를_재연결_요청에_전달한다() throws Exception {
        SseEmitter emitter = new SseEmitter();
        given(connectionManager.connect("42")).willReturn(emitter);

        mockMvc.perform(get("/api/auctions/stream").header("Last-Event-ID", "42"))
                .andExpect(request().asyncStarted());

        verify(connectionManager).connect("42");
    }
}
