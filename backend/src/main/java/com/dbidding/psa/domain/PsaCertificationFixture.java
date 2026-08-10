package com.dbidding.psa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "psa_certification_fixtures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PsaCertificationFixture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "certification_number", nullable = false, unique = true, length = 10)
    private String certificationNumber;

    @Column(name = "item_id", nullable = false)
    private Integer itemId;

    public PsaCertificationFixture(String certificationNumber, Integer itemId) {
        this.certificationNumber = certificationNumber;
        this.itemId = itemId;
    }
}
