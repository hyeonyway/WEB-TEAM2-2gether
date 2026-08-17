package com.dbidding.account.warning;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderCancellationWarningServiceTest {

	@Test
	void 주문_취소_경고_발급을_공유_발급기에_위임한다() {
		UserWarningIssuer issuer = Mockito.mock(UserWarningIssuer.class);

		new OrderCancellationWarningService(issuer).issue(7, 10, UserWarningReason.BUYER_CANCELLED);

		verify(issuer).issue(7, 10, UserWarningReason.BUYER_CANCELLED);
	}
}
