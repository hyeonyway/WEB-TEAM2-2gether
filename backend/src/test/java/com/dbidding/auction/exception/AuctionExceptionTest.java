package com.dbidding.auction.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuctionExceptionTest {

	@Test
	void 경매를_찾을_수_없으면_도메인_오류_코드와_404를_가진다() {
		AuctionException exception = AuctionException.notFound();

		assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(exception.getCode()).isEqualTo("AUCTION_NOT_FOUND");
		assertThat(exception.getMessage()).isEqualTo("경매를 찾을 수 없습니다.");
	}

	@Test
	void 판매자_본인_입찰은_도메인_오류_코드와_403을_가진다() {
		AuctionException exception = AuctionException.sellerBidForbidden();

		assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(exception.getCode()).isEqualTo("AUCTION_SELLER_BID_FORBIDDEN");
	}
}
