package com.dbidding.global.security.session;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.stereotype.Component;

@Component
public class SessionSseConnectionRegistry {

	private final ConcurrentMap<String, Set<SseEmitter>> emittersBySessionId = new ConcurrentHashMap<>();

	public void register(String sessionId, SseEmitter emitter) {
		emittersBySessionId.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
	}

	public void unregister(String sessionId, SseEmitter emitter) {
		emittersBySessionId.computeIfPresent(sessionId, (ignored, emitters) -> {
			emitters.remove(emitter);
			return emitters.isEmpty() ? null : emitters;
		});
	}

	public void disconnect(String sessionId) {
		Set<SseEmitter> emitters = emittersBySessionId.remove(sessionId);
		if (emitters != null) emitters.forEach(this::complete);
	}

	private void complete(SseEmitter emitter) {
		try {
			emitter.complete();
		} catch (IllegalStateException ignored) {
			// 이미 완료된 연결은 registry에서 제거하는 것으로 충분하다.
		}
	}
}
