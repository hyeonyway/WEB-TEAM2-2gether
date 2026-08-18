package com.dbidding.psa.repository;

import com.dbidding.psa.domain.PsaCertificationFixture;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PsaCertificationFixtureRepository extends JpaRepository<PsaCertificationFixture, Integer> {

    Optional<PsaCertificationFixture> findByCertificationNumber(String certificationNumber);

    Optional<PsaCertificationFixture> findFirstByOrderByIdAsc();
}
