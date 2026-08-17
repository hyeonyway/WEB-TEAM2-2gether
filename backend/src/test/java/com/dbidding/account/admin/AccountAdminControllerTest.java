package com.dbidding.account.admin;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dbidding.account.admin.dto.AdminAccountPageResponse;
import com.dbidding.account.admin.dto.AdminAccountResponse;
import com.dbidding.account.admin.dto.UserWarningResponse;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.exception.AccountNotFoundException;
import com.dbidding.account.warning.AdminWarningService;
import com.dbidding.account.warning.UserWarningReason;
import com.dbidding.global.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountAdminController.class)
class AccountAdminControllerTest {

	private static final Integer ADMIN_ID = 1;
	private static final Integer TARGET_ID = 2;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccountAdminQueryService queryService;

	@MockitoBean
	private AccountSuspensionService suspensionService;

	@MockitoBean
	private AdminWarningService adminWarningService;

	@MockitoBean
	private CurrentUserProvider currentUserProvider;

	@BeforeEach
	void setUp() {
		given(currentUserProvider.getCurrentUserId()).willReturn(ADMIN_ID);
	}

	@Test
	void 관리자가_검색어와_페이지를_지정해_회원_목록을_조회한다() throws Exception {
		given(queryService.findAccounts(ADMIN_ID, 1, 20, "피카츄", null, false)).willReturn(new AdminAccountPageResponse(
			List.of(new AdminAccountResponse(
				TARGET_ID, "pikachu@example.com", "피카츄", AccountRole.USER, AccountStatus.SUSPENDED,
				Instant.parse("2026-08-01T00:00:00Z"), 1, Instant.parse("2026-09-01T00:00:00Z")
			)),
			1, 20, 21, 2
		));

		mockMvc.perform(get("/api/admin/users")
				.queryParam("page", "1")
				.queryParam("size", "20")
				.queryParam("keyword", "피카츄"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].id").value(TARGET_ID))
			.andExpect(jsonPath("$.content[0].email").value("pikachu@example.com"))
			.andExpect(jsonPath("$.content[0].status").value("SUSPENDED"))
			.andExpect(jsonPath("$.content[0].active_warning_count").value(1))
			.andExpect(jsonPath("$.content[0].latest_active_warning_expires_at").value("2026-09-01T00:00:00Z"))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.total_elements").value(21));

		verify(queryService).findAccounts(ADMIN_ID, 1, 20, "피카츄", null, false);
	}

	@Test
	void 관리자가_상태와_경고_필터를_지정해_회원_목록을_조회한다() throws Exception {
		given(queryService.findAccounts(ADMIN_ID, 0, 20, null, AccountStatus.SUSPENDED, true))
			.willReturn(new AdminAccountPageResponse(List.of(), 0, 20, 0, 0));

		mockMvc.perform(get("/api/admin/users")
				.queryParam("status", "SUSPENDED")
				.queryParam("only_warned", "true"))
			.andExpect(status().isOk());

		verify(queryService).findAccounts(ADMIN_ID, 0, 20, null, AccountStatus.SUSPENDED, true);
	}

	@Test
	void 관리자가_대상의_경고_이력을_최신순으로_조회한다() throws Exception {
		given(queryService.findWarnings(ADMIN_ID, TARGET_ID)).willReturn(List.of(new UserWarningResponse(
			10L, 100, UserWarningReason.BUYER_CANCELLED,
			Instant.parse("2026-08-10T00:00:00Z"), Instant.parse("2026-09-09T00:00:00Z")
		)));

		mockMvc.perform(get("/api/admin/users/{userId}/warnings", TARGET_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].order_id").value(100))
			.andExpect(jsonPath("$[0].reason").value("BUYER_CANCELLED"))
			.andExpect(jsonPath("$[0].issued_at").value("2026-08-10T00:00:00Z"));

		verify(queryService).findWarnings(ADMIN_ID, TARGET_ID);
	}

	@Test
	void 일반_사용자의_목록_요청은_403_공통_오류_응답을_반환한다() throws Exception {
		given(queryService.findAccounts(ADMIN_ID, 0, 20, null, null, false)).willThrow(new AccountAdminAccessDeniedException());

		mockMvc.perform(get("/api/admin/users"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCOUNT_ADMIN_ACCESS_DENIED"));
	}

	@Test
	void 존재하지_않는_대상의_경고_이력_요청은_404를_반환한다() throws Exception {
		given(queryService.findWarnings(ADMIN_ID, TARGET_ID)).willThrow(new AccountNotFoundException());

		mockMvc.perform(get("/api/admin/users/{userId}/warnings", TARGET_ID))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
	}

	@Test
	void 관리자가_계정을_정지한다() throws Exception {
		mockMvc.perform(post("/api/admin/users/{userId}/suspend", TARGET_ID))
			.andExpect(status().isNoContent());

		verify(suspensionService).suspend(ADMIN_ID, TARGET_ID);
	}

	@Test
	void 관리자가_계정을_활성화한다() throws Exception {
		mockMvc.perform(post("/api/admin/users/{userId}/activate", TARGET_ID))
			.andExpect(status().isNoContent());

		verify(suspensionService).activate(ADMIN_ID, TARGET_ID);
	}

	@Test
	void 관리자가_계정에_경고를_준다() throws Exception {
		mockMvc.perform(post("/api/admin/users/{userId}/warn", TARGET_ID))
			.andExpect(status().isNoContent());

		verify(adminWarningService).warn(ADMIN_ID, TARGET_ID);
	}
}
