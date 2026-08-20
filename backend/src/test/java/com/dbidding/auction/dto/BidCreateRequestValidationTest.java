package com.dbidding.auction.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.wallet.domain.WalletAmountPolicy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BidCreateRequestValidationTest {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void 입찰가는_1원_미만일_수_없다() {
        BidCreateRequest request = new BidCreateRequest(0L);

        Set<ConstraintViolation<BidCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("price");
    }

    @Test
    void 입찰가는_지갑_최대_잔액을_초과할_수_없다() {
        BidCreateRequest request = new BidCreateRequest(WalletAmountPolicy.MAX_BALANCE + 1);

        Set<ConstraintViolation<BidCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("price");
    }

    @Test
    void 지갑_최대_잔액까지의_입찰가는_유효하다() {
        BidCreateRequest request = new BidCreateRequest(WalletAmountPolicy.MAX_BALANCE);

        assertThat(validator.validate(request)).isEmpty();
    }
}
