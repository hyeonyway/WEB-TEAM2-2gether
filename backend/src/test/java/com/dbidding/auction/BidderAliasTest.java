package com.dbidding.auction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BidderAliasTest {

    @Test
    void 한_자리_id는_그대로_마스킹한다() {
        assertThat(BidderAlias.mask(7)).isEqualTo("user-7***");
    }

    @Test
    void 두_자리_id는_그대로_마스킹한다() {
        assertThat(BidderAlias.mask(42)).isEqualTo("user-42***");
    }

    @Test
    void 세_자리_이상_id는_앞_두_자리만_남긴다() {
        assertThat(BidderAlias.mask(123)).isEqualTo("user-12***");
        assertThat(BidderAlias.mask(100)).isEqualTo("user-10***");
    }

    @Test
    void null_id는_빈_문자열을_반환한다() {
        assertThat(BidderAlias.mask(null)).isEqualTo("");
    }
}
