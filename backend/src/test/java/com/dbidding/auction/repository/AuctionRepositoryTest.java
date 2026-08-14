package com.dbidding.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class AuctionRepositoryTest {

    @Test
    void PSA_등급은_접두사와_대소문자_공백을_정규화해_검색한다() throws NoSuchMethodException {
        Method method = AuctionRepository.class.getMethod(
                "searchByCursor",
                String.class,
                String.class,
                Collection.class,
                String.class,
                Integer.class,
                Long.class,
                Long.class,
                Instant.class,
                Instant.class,
                Integer.class,
                boolean.class,
                Instant.class,
                Pageable.class
        );
        String query = method.getAnnotation(Query.class).value();

        assertThat(query)
                .contains("replace(upper(trim(c.psaGrade)), 'PSA ', '')")
                .contains("replace(upper(trim(:psaGrade)), 'PSA ', '')")
                .contains(":activeOnly = false or a.closeTime > :now");
        assertThat(method.getReturnType()).isEqualTo(List.class);
        assertThat(query)
                .contains("a.bidCount < :bidCountCursor")
                .contains("a.currentPrice < :priceCursor")
                .contains("a.currentPrice > :priceCursor")
                .contains("a.changeRateBasisPoints < :changeRateCursor")
                .contains("a.changeRateBasisPoints = :changeRateCursor and a.id < :cursorId")
                .contains("a.openTime < :openTimeCursor")
                .contains("a.openTime = :openTimeCursor and a.id < :cursorId")
                .contains("case when :sort = 'ENDING_SOON' then a.closeTime end asc")
                .contains("case when :sort = 'LATEST' then a.openTime end desc")
                .contains("case when :sort = 'CHANGE_HIGH' then a.changeRateBasisPoints end desc")
                .contains("a.id < :cursorId");
    }
}
