package com.dbidding.global.security.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SessionSseConnectionRegistryTest {

	@Test
	void 세션을_종료하면_해당_세션의_SSE_연결만_종료한다() {
		SessionSseConnectionRegistry registry = new SessionSseConnectionRegistry();
		SseEmitter first = mock(SseEmitter.class);
		SseEmitter second = mock(SseEmitter.class);
		registry.register("session-a", first);
		registry.register("session-b", second);

		registry.disconnect("session-a");

		verify(first).complete();
		verifyNoInteractions(second);
	}

	@Test
	void 종료된_세션에는_뒤늦게_도착한_SSE_연결을_등록하지_않는다() {
		SessionSseConnectionRegistry registry = new SessionSseConnectionRegistry();
		SseEmitter lateEmitter = mock(SseEmitter.class);

		registry.disconnect("session-a");

		registry.register("session-a", lateEmitter);

		verify(lateEmitter).complete();
	}
}
