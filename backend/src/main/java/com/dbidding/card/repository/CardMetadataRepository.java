package com.dbidding.card.repository;

import com.dbidding.card.domain.CardMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardMetadataRepository extends JpaRepository<CardMetadata, Integer> {
    @Query(
            value = """
                    select c.*
                    from card_metadata c
                    left join item_statistics s
                      on s.item_id = c.id
                    where (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%')))
                      and (:psaGrade is null or
                           replace(upper(trim(c.psa_grade)), 'PSA ', '') =
                           replace(upper(trim(:psaGrade)), 'PSA ', ''))
                    order by
                      case when :sort = 'PRICE'
                           then coalesce(s.latest_price, s.average_price_30d, 0)
                      end desc,
                      case when :sort = 'FAVORITE'
                           then coalesce(s.wishlist_count, 0)
                      end desc,
                      case when :sort = 'REGISTERED'
                           then c.id
                      end asc,
                      c.id desc
                    """,
            countQuery = """
                    select count(*)
                    from card_metadata c
                    where (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%')))
                      and (:psaGrade is null or
                           replace(upper(trim(c.psa_grade)), 'PSA ', '') =
                           replace(upper(trim(:psaGrade)), 'PSA ', ''))
                    """,
            nativeQuery = true
    )
    Page<CardMetadata> search(@Param("keyword") String keyword,
                              @Param("psaGrade") String psaGrade,
                              @Param("sort") String sort,
                              Pageable pageable);
}
