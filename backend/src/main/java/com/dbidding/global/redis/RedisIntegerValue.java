package com.dbidding.global.redis;

import java.math.BigDecimal;

public final class RedisIntegerValue {

	private RedisIntegerValue() {
	}

	public static long parseLongExact(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Redis 정수 값은 null일 수 없습니다.");
		}
		try {
			return new BigDecimal(value).longValueExact();
		} catch (NumberFormatException | ArithmeticException exception) {
			throw new IllegalArgumentException("Redis 정수 값이 올바르지 않습니다: " + value, exception);
		}
	}
}
