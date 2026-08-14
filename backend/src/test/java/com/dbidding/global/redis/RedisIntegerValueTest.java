package com.dbidding.global.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RedisIntegerValueTest {

	@Test
	void 과거_지수_표기_정수를_정확한_long으로_읽는다() {
		assertThat(RedisIntegerValue.parseLongExact("1.000000512e+14"))
			.isEqualTo(100_000_051_200_000L);
	}

	@Test
	void 소수와_long_범위_초과값은_거절한다() {
		assertThatThrownBy(() -> RedisIntegerValue.parseLongExact("1.5"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> RedisIntegerValue.parseLongExact("9223372036854775808"))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
