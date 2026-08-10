package com.dbidding.global.security.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.stereotype.Component;

@Component
public class SessionSseConnectionRegistry {

	private static final Duration TERMINATED_SESSION_RETENTION = Duration.ofMinutes(1);

	private final Object monitor = new Object();
	private final ConcurrentMap<String, Set<SseEmitter>> emittersBySessionId = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Instant> terminatedSessionUntil = new ConcurrentHashMap<>();
	private final Clock clock;
	private final Duration terminatedSessionRetention;

	public SessionSseConnectionRegistry() {
		this(Clock.systemUTC(), TERMINATED_SESSION_RETENTION);
	}

	SessionSseConnectionRegistry(Clock clock, Duration terminatedSessionRetention) {
		this.clock = clock;
		this.terminatedSessionRetention = terminatedSessionRetention;
	}

	public boolean register(String sessionId, SseEmitter emitter) {
		boolean terminated;
		synchronized (monitor) {
			Instant now = clock.instant();
			removeExpiredTerminatedSessions(now);
			terminated = terminatedSessionUntil.containsKey(sessionId);
			if (!terminated) {
				emittersBySessionId.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
			}
		}
		if (terminated) complete(emitter);
		return !terminated;
	}

	public void unregister(String sessionId, SseEmitter emitter) {
		synchronized (monitor) {
			emittersBySessionId.computeIfPresent(sessionId, (ignored, emitters) -> {
				emitters.remove(emitter);
				return emitters.isEmpty() ? null : emitters;
			});
		}
	}

	public void disconnect(String sessionId) {
		Set<SseEmitter> emitters;
		synchronized (monitor) {
			Instant now = clock.instant();
			removeExpiredTerminatedSessions(now);
			terminatedSessionUntil.put(sessionId, now.plus(terminatedSessionRetention));
			emitters = emittersBySessionId.remove(sessionId);
		}
		if (emitters != null) emitters.forEach(this::complete);
	}

	private void removeExpiredTerminatedSessions(Instant now) {
		terminatedSessionUntil.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
	}

	private void complete(SseEmitter emitter) {
		try {
			emitter.complete();
		} catch (IllegalStateException ignored) {
			// 이미 완료된 연결은 registry에서 제거하는 것으로 충분하다.
		}
	}
}
