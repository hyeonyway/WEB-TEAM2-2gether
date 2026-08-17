package com.dbidding.account.warning;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderCancellationWarningService {

	private final UserWarningIssuer userWarningIssuer;

	public void issue(Integer userId, Integer orderId, UserWarningReason reason) {
		userWarningIssuer.issue(userId, orderId, reason);
	}
}
