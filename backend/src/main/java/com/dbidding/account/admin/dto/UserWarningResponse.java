package com.dbidding.account.admin.dto;

import com.dbidding.account.warning.UserWarning;
import com.dbidding.account.warning.UserWarningReason;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record UserWarningResponse(
	Long id,
	@JsonProperty("order_id") Integer orderId,
	UserWarningReason reason,
	@JsonProperty("issued_at") Instant issuedAt,
	@JsonProperty("expires_at") Instant expiresAt
) {
	public static UserWarningResponse from(UserWarning warning) {
		return new UserWarningResponse(
			warning.getId(), warning.getOrderId(), warning.getReason(), warning.getIssuedAt(), warning.getExpiresAt()
		);
	}
}
