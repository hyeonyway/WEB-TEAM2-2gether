package com.dbidding.auction.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuctionCreateRequestValidationTest {
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
    void 경매명과_설명은_데이터베이스_컬럼_길이를_초과할_수_없다() {
        AuctionCreateRequest request = request("a".repeat(256), "b".repeat(256), "upload-token");

        Set<ConstraintViolation<AuctionCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("auctionName", "description");
    }

    @Test
    void 이미지_토큰은_각각_255자_이하여야_한다() {
        AuctionCreateRequest request = request("경매", "설명", "a".repeat(256));

        Set<ConstraintViolation<AuctionCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("imageUploadTokens[0].<list element>");
    }

    @Test
    void 데이터베이스_길이_이내의_등록_요청은_유효하다() {
        AuctionCreateRequest request = request("a".repeat(255), "b".repeat(255), "c".repeat(255));

        assertThat(validator.validate(request)).isEmpty();
    }

    private AuctionCreateRequest request(String auctionName, String description, String imageToken) {
        return new AuctionCreateRequest(
                1,
                auctionName,
                description,
                null,
                null,
                List.of(imageToken),
                10_000L,
                1_000L,
                null,
                12,
                3_000L
        );
    }
}
