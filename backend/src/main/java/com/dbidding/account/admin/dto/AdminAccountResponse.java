package com.dbidding.account.admin.dto;

import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.domain.AccountStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record AdminAccountResponse(
	Integer id,
	String email,
	String nickname,
	AccountRole role,
	AccountStatus status,
	@JsonProperty("created_at") Instant createdAt,
	@JsonProperty("active_warning_count") long activeWarningCount,
	@JsonProperty("latest_active_warning_expires_at") Instant latestActiveWarningExpiresAt
) {
}
