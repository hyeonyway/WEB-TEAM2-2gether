package com.dbidding.global.logging;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

public class SlackWebhookAppender extends AppenderBase<ILoggingEvent> {

	private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration DEFAULT_RATE_LIMIT_WINDOW = Duration.ofSeconds(60);
	private static final int DEFAULT_MAX_ALERTS_PER_WINDOW = 3;
	private static final int MAX_STACK_EXCERPT_LINES = 10;
	private static final int MAX_STACK_EXCERPT_LENGTH = 1_400;
	private static final String NO_EXCEPTION = "NO_EXCEPTION";
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
		.ofPattern("yyyy-MM-dd HH:mm:ss z")
		.withZone(ZoneId.of("Asia/Seoul"));

	private String webhookUrl = "";
	private String service = "dbidding";
	private String environment = "default";
	private int rateLimitWindowSeconds = (int)DEFAULT_RATE_LIMIT_WINDOW.toSeconds();
	private int maximumAlertsPerWindow = DEFAULT_MAX_ALERTS_PER_WINDOW;
	private HttpClient httpClient;
	private SlackAlertRateLimiter rateLimiter;

	public void setWebhookUrl(String webhookUrl) {
		this.webhookUrl = webhookUrl;
	}

	public void setService(String service) {
		this.service = service;
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public void setRateLimitWindowSeconds(int rateLimitWindowSeconds) {
		this.rateLimitWindowSeconds = rateLimitWindowSeconds;
	}

	public void setMaximumAlertsPerWindow(int maximumAlertsPerWindow) {
		this.maximumAlertsPerWindow = maximumAlertsPerWindow;
	}

	@Override
	public void start() {
		try {
			rateLimiter = new SlackAlertRateLimiter(
				Duration.ofSeconds(rateLimitWindowSeconds),
				maximumAlertsPerWindow
			);
			httpClient = HttpClient.newBuilder()
				.connectTimeout(HTTP_TIMEOUT)
				.build();
			super.start();
		} catch (RuntimeException exception) {
			addError("Slack appender를 초기화하지 못했습니다.", exception);
		}
	}

	@Override
	protected void append(ILoggingEvent event) {
		if (webhookUrl == null || webhookUrl.isBlank()) {
			return;
		}
		try {
			IThrowableProxy throwable = event.getThrowableProxy();
			String exceptionType = throwable == null ? NO_EXCEPTION : throwable.getClassName();
			Instant timestamp = Instant.ofEpochMilli(event.getTimeStamp());
			SlackAlertRateLimiter.Decision decision = rateLimiter.acquire(
				event.getLoggerName(),
				exceptionType,
				timestamp
			);
			if (decision.suppressedCount() > 0) {
				send(summaryPayload(event, exceptionType, decision.suppressedCount(), timestamp));
			}
			if (decision.send()) {
				send(eventPayload(event, exceptionType, timestamp));
			}
		} catch (RuntimeException exception) {
			addError("Slack 로그 경고를 전송하지 못했습니다.", exception);
		}
	}

	private void send(String payload) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
			.timeout(HTTP_TIMEOUT)
			.header("Content-Type", "application/json; charset=UTF-8")
			.POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
			.build();
		httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
			.exceptionally(exception -> {
				addError("Slack 로그 경고 HTTP 요청이 실패했습니다.", exception);
				return null;
			});
	}

	private String eventPayload(ILoggingEvent event, String exceptionType, Instant timestamp) {
		return payload(
			event.getLevel(),
			event.getLoggerName(),
			event.getThreadName(),
			timestamp,
			event.getFormattedMessage(),
			exceptionType,
			stackExcerpt(event.getThrowableProxy())
		);
	}

	private String summaryPayload(
		ILoggingEvent event,
		String exceptionType,
		long suppressedCount,
		Instant timestamp
	) {
		return payload(
			Level.WARN,
			event.getLoggerName(),
			event.getThreadName(),
			timestamp,
			"최근 " + rateLimitWindowSeconds + "초간 같은 로그 " + suppressedCount + "건을 억제했습니다.",
			exceptionType,
			""
		);
	}

	private String payload(
		Level level,
		String logger,
		String thread,
		Instant timestamp,
		String message,
		String exceptionType,
		String stackExcerpt
	) {
		return "{" +
			field("level_emoji", level == Level.ERROR ? "🔴" : "🟡") + "," +
			field("level", level.levelStr) + "," +
			field("service", service) + "," +
			field("environment", environment) + "," +
			field("logger", logger) + "," +
			field("thread", thread) + "," +
			field("timestamp", TIMESTAMP_FORMATTER.format(timestamp)) + "," +
			field("message", message) + "," +
			field("exception_type", exceptionType) + "," +
			field("stack_excerpt", stackExcerpt) +
			"}";
	}

	/**
	 * Slack Workflow Builder의 "메시지 보내기" 단계는 변수 값을 rich_text로 그대로 넣는다 —
	 * mrkdwn 텍스트와 달리 렌더링 시점에 백틱을 코드블럭으로 재해석하지 않는다. 그래서 값에
	 * 코드펜스를 넣어봐야 리터럴 문자로만 보이고 코드블럭이 되지 않는다(실제 확인됨). 코드블럭은
	 * 포기하고 일반 텍스트로 보낸다.
	 */
	private String stackExcerpt(IThrowableProxy throwable) {
		if (throwable == null) {
			return "";
		}
		String excerpt = ThrowableProxyUtil.asString(throwable)
			.lines()
			.limit(MAX_STACK_EXCERPT_LINES)
			.reduce((first, second) -> first + "\n" + second)
			.orElse("");
		return excerpt.length() <= MAX_STACK_EXCERPT_LENGTH
			? excerpt
			: excerpt.substring(0, MAX_STACK_EXCERPT_LENGTH) + "…";
	}

	private String field(String name, String value) {
		return "\"" + name + "\":\"" + escape(value) + "\"";
	}

	private String escape(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder escaped = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '"' -> escaped.append("\\\"");
				case '\\' -> escaped.append("\\\\");
				case '\b' -> escaped.append("\\b");
				case '\f' -> escaped.append("\\f");
				case '\n' -> escaped.append("\\n");
				case '\r' -> escaped.append("\\r");
				case '\t' -> escaped.append("\\t");
				default -> {
					if (character < 0x20) {
						escaped.append("\\u%04x".formatted((int)character));
					} else {
						escaped.append(character);
					}
				}
			}
		}
		return escaped.toString();
	}
}
