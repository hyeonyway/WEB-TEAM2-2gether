package com.dbidding.wallet.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dbidding.global.security.CurrentUserProvider;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.exception.InvalidWalletBalanceException;
import com.dbidding.wallet.service.WalletService;

@WebMvcTest(WalletController.class)
class WalletControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WalletService walletService;

	@MockitoBean
	private CurrentUserProvider currentUserProvider;

	@BeforeEach
	void setUp() {
		given(currentUserProvider.getCurrentUserId()).willReturn(1);
	}

	@Test
	void 로그인_사용자의_총액_동결액_가용액을_조회한다() throws Exception {
		given(walletService.getBalance(1))
			.willReturn(new WalletBalanceResponse(100_000L, 30_000L, 70_000L));

		mockMvc.perform(get("/api/wallet"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalBalance").value(100_000))
			.andExpect(jsonPath("$.frozenBalance").value(30_000))
			.andExpect(jsonPath("$.availableBalance").value(70_000));
	}

	@Test
	void 손상된_잔액_상태는_500으로_반환한다() throws Exception {
		given(walletService.getBalance(1))
			.willThrow(new InvalidWalletBalanceException());

		mockMvc.perform(get("/api/wallet"))
			.andExpect(status().isInternalServerError());
	}
}
