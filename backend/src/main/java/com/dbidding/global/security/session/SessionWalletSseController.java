package com.dbidding.global.security.session;

import com.dbidding.global.security.CurrentUser;
import com.dbidding.wallet.sse.WalletSseConnectionManager;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "session")
public class SessionWalletSseController {
    private final WalletSseConnectionManager connectionManager;

    @GetMapping(value = "/api/me/wallet/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUser Integer userId, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return connectionManager.connect(userId);
    }
}
