package com.dbidding.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenHasherTest {

    private final RefreshTokenHasher hasher = new RefreshTokenHasher();

    @Test
    void Refresh_Token을_64자_SHA256_소문자_hex로_변환한다() {
        String hash = hasher.hash("refresh-token");

        assertThat(hash)
            .isEqualTo("0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120")
            .matches("[0-9a-f]{64}");
    }
}
