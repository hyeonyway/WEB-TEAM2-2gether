package com.dbidding.wallet.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dbidding.global.security.CurrentUserProvider;
import com.dbidding.wallet.dto.WalletTransactionResponse;
import com.dbidding.wallet.exception.IdempotencyConflictException;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import com.dbidding.wallet.exception.InvalidIdempotencyKeyException;
import com.dbidding.wallet.exception.InvalidWalletAmountException;
import com.dbidding.wallet.exception.WalletNotFoundException;
import com.dbidding.wallet.service.WalletService;

@WebMvcTest(WalletTransactionController.class)
class WalletTransactionControllerTest {

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
	void 로그인_사용자가_idempotency_key로_포인트를_충전한다() throws Exception {
		given(walletService.charge(1, 10_000L, "charge-key"))
			.willReturn(new WalletTransactionResponse(
				10L,
				"CHARGE",
				10_000L,
				10_000L
			));

		mockMvc.perform(post("/api/wallet/charges")
				.header("Idempotency-Key", "charge-key")
				.contentType(APPLICATION_JSON)
				.content("{\"amount\":10000}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.transactionId").value(10))
			.andExpect(jsonPath("$.transactionType").value("CHARGE"))
			.andExpect(jsonPath("$.amount").value(10_000))
			.andExpect(jsonPath("$.balance").value(10_000));
	}

	@Test
	void 로그인_사용자가_idempotency_key로_포인트를_환불한다() throws Exception {
		given(walletService.refund(1, 3_000L, "refund-key"))
			.willReturn(new WalletTransactionResponse(
				11L,
				"REFUND",
				-3_000L,
				7_000L
			));

		mockMvc.perform(post("/api/wallet/refunds")
				.header("Idempotency-Key", "refund-key")
				.contentType(APPLICATION_JSON)
				.content("{\"amount\":3000}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.transactionId").value(11))
			.andExpect(jsonPath("$.transactionType").value("REFUND"))
			.andExpect(jsonPath("$.amount").value(-3_000))
			.andExpect(jsonPath("$.balance").value(7_000));
	}

	@Test
	void idempotency_key가_없거나_비어_있으면_400이다() throws Exception {
		mockMvc.perform(post("/api/wallet/refunds")
				.contentType(APPLICATION_JSON)
				.content("{\"amount\":1000}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/wallet/refunds")
				.header("Idempotency-Key", " ")
				.contentType(APPLICATION_JSON)
				.content("{\"amount\":1000}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void idempotency_key가_64자를_초과하면_400이다() throws Exception {
		mockMvc.perform(post("/api/wallet/charges")
				.header("Idempotency-Key", "a".repeat(65))
				.contentType(APPLICATION_JSON)
				.content("{\"amount\":1000}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 요청_금액이_양수가_아니면_400이다() throws Exception {
		mockMvc.perform(post("/api/wallet/charges")
				.header("Idempotency-Key", "charge-key")
				.contentType(APPLICATION_JSON)
				.content("{\"amount\":0}"))
			.andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@MethodSource("domainExceptionMappings")
	void Wallet_도메인_예외를_약속한_HTTP_상태로_반환한다(
		RuntimeException exception,
		HttpStatus expectedStatus
	) throws Exception {
		given(walletService.charge(1, 10_000L, "charge-key"))
			.willThrow(exception);

		mockMvc.perform(post("/api/wallet/charges")
				.header("Idempotency-Key", "charge-key")
				.contentType(APPLICATION_JSON)
				.content("{\"amount\":10000}"))
			.andExpect(status().is(expectedStatus.value()));
	}

	private static Stream<Arguments> domainExceptionMappings() {
		return Stream.of(
			Arguments.of(new InvalidIdempotencyKeyException(), HttpStatus.BAD_REQUEST),
			Arguments.of(
				new InvalidWalletAmountException("유효하지 않은 금액입니다."),
				HttpStatus.BAD_REQUEST
			),
			Arguments.of(new WalletNotFoundException(), HttpStatus.NOT_FOUND),
			Arguments.of(new InsufficientAvailableBalanceException(), HttpStatus.CONFLICT),
			Arguments.of(new IdempotencyConflictException(), HttpStatus.CONFLICT)
		);
	}
}
