package com.dbidding.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MyWarningSummaryResponse(
	@JsonProperty("active_warning_count") long activeWarningCount,
	@JsonProperty("suspension_threshold") long suspensionThreshold
) {
}
