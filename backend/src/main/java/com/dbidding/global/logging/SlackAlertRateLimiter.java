package com.dbidding.global.logging;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

final class SlackAlertRateLimiter {

	private final Duration windowDuration;
	private final int maximumAlertsPerWindow;
	private final Map<AlertKey, Window> windows = new HashMap<>();

	SlackAlertRateLimiter(Duration windowDuration, int maximumAlertsPerWindow) {
		if (windowDuration.isNegative() || windowDuration.isZero()) {
			throw new IllegalArgumentException("레이트리밋 윈도우는 0보다 커야 합니다.");
		}
		if (maximumAlertsPerWindow <= 0) {
			throw new IllegalArgumentException("윈도우당 최대 알림 수는 1 이상이어야 합니다.");
		}
		this.windowDuration = windowDuration;
		this.maximumAlertsPerWindow = maximumAlertsPerWindow;
	}

	synchronized Decision acquire(String loggerName, String exceptionType, Instant now) {
		AlertKey key = new AlertKey(loggerName, exceptionType);
		Window currentWindow = windows.get(key);
		if (currentWindow == null || !now.isBefore(currentWindow.endsAt())) {
			long suppressedCount = currentWindow == null ? 0 : currentWindow.suppressedCount();
			windows.put(key, new Window(now.plus(windowDuration), 1, 0));
			return new Decision(true, suppressedCount);
		}
		if (currentWindow.sentCount() < maximumAlertsPerWindow) {
			windows.put(key, currentWindow.withSentCount(currentWindow.sentCount() + 1));
			return new Decision(true, 0);
		}
		windows.put(key, currentWindow.withSuppressedCount(currentWindow.suppressedCount() + 1));
		return new Decision(false, 0);
	}

	record Decision(boolean send, long suppressedCount) {
	}

	private record AlertKey(String loggerName, String exceptionType) {
	}

	private record Window(Instant endsAt, int sentCount, long suppressedCount) {

		private Window withSentCount(int sentCount) {
			return new Window(endsAt, sentCount, suppressedCount);
		}

		private Window withSuppressedCount(long suppressedCount) {
			return new Window(endsAt, sentCount, suppressedCount);
		}
	}
}
