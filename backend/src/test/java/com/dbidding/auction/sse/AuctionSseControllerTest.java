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
        given(connectionManager.connect()).willReturn(emitter);

        mockMvc.perform(get("/api/auctions/stream"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(header().string("X-Accel-Buffering", "no"));

        verify(connectionManager).connect();
    }
}
