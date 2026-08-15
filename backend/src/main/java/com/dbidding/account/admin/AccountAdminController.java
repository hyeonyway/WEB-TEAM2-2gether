package com.dbidding.account.admin;

import com.dbidding.account.admin.dto.AdminAccountPageResponse;
import com.dbidding.account.admin.dto.UserWarningResponse;
import com.dbidding.global.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AccountAdminController {

	private final AccountAdminQueryService queryService;
	private final AccountSuspensionService suspensionService;

	@GetMapping
	public AdminAccountPageResponse findAccounts(
		@CurrentUser Integer userId,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
		@RequestParam(required = false) String keyword
	) {
		return queryService.findAccounts(userId, page, size, keyword);
	}

	@GetMapping("/{userId}/warnings")
	public List<UserWarningResponse> findWarnings(@CurrentUser Integer actorId, @PathVariable Integer userId) {
		return queryService.findWarnings(actorId, userId);
	}

	@PostMapping("/{userId}/suspend")
	public ResponseEntity<Void> suspend(@CurrentUser Integer actorId, @PathVariable Integer userId) {
		suspensionService.suspend(actorId, userId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{userId}/activate")
	public ResponseEntity<Void> activate(@CurrentUser Integer actorId, @PathVariable Integer userId) {
		suspensionService.activate(actorId, userId);
		return ResponseEntity.noContent().build();
	}
}
