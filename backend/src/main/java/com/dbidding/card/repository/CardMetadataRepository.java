package com.dbidding.card.repository;

import com.dbidding.card.domain.CardMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardMetadataRepository extends JpaRepository<CardMetadata, Long> {
    @Query(
            value = """
                    select c.*
                    from card_metadata c
                    left join item_statistics s
                      on s.item_id = c.id
                     and s.statistics_date = (
                         select max(latest.statistics_date)
                         from item_statistics latest
                         where latest.item_id = c.id
                     )
                    where (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%')))
                      and (:psaGrade is null or c.psa_grade = :psaGrade)
                    order by
                      case when :sort = 'PRICE'
                           then coalesce(s.latest_price, s.avg_price, c.reference_price, 0)
                      end desc,
                      case when :sort = 'FAVORITE'
                           then coalesce(c.favorite_count, 0)
                      end desc,
                      c.id desc
                    """,
            countQuery = """
                    select count(*)
                    from card_metadata c
                    where (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%')))
                      and (:psaGrade is null or c.psa_grade = :psaGrade)
                    """,
            nativeQuery = true
    )
    Page<CardMetadata> search(@Param("keyword") String keyword,
                              @Param("psaGrade") Integer psaGrade,
                              @Param("sort") String sort,
                              Pageable pageable);
}
