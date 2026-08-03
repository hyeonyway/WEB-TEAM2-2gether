package com.dbidding.auction.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class AuctionRepositoryTest {

    @Test
    void PSA_등급은_접두사와_대소문자_공백을_정규화해_검색한다() throws NoSuchMethodException {
        Method method = AuctionRepository.class.getMethod(
                "search",
                String.class,
                String.class,
                Collection.class,
                String.class,
                Pageable.class
        );
        String query = method.getAnnotation(Query.class).value();

        assertThat(query)
                .contains("replace(upper(trim(c.psaGrade)), 'PSA ', '')")
                .contains("replace(upper(trim(:psaGrade)), 'PSA ', '')");
        assertThat(method.getReturnType()).isEqualTo(Page.class);
    }
}
