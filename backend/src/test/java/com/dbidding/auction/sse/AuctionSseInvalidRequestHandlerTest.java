package com.dbidding.auction.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.global.exception.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.MissingServletRequestParameterException;

class AuctionSseInvalidRequestHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void SSE_필수_파라미터_오류만_JSON_400으로_직접_응답한다() throws Exception {
        HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        new AuctionSseInvalidRequestHandler().handleMissingParameter(
                new MissingServletRequestParameterException("auctionIds", "Set"), response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(objectMapper.readValue(writer.toString(), ApiErrorResponse.class))
                .isEqualTo(new ApiErrorResponse("INVALID_REQUEST", "요청 정보를 확인해 주세요."));
    }
}
