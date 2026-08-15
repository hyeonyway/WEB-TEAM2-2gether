package com.dbidding.account.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AdminAccountPageResponse(
	List<AdminAccountResponse> content,
	int page,
	int size,
	@JsonProperty("total_elements") long totalElements,
	@JsonProperty("total_pages") int totalPages
) {
}
