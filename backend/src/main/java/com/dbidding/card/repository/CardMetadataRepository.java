package com.dbidding.card.repository;

import com.dbidding.card.domain.CardMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardMetadataRepository extends JpaRepository<CardMetadata, Long> {
    @Query("""
            select c from CardMetadata c
            where (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%')))
              and (:psaGrade is null or c.psaGrade = :psaGrade)
            """)
    Page<CardMetadata> search(@Param("keyword") String keyword,
                              @Param("psaGrade") Integer psaGrade,
                              Pageable pageable);
}
