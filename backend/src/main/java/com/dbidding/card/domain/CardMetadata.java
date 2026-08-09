package com.dbidding.card.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "card_metadata")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_set_id", nullable = false)
    private CardSet cardSet;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "language", length = 20)
    private String language;

    @Column(name = "psa_grade", length = 15)
    private String psaGrade;

    @Column(name = "rarity", length = 30)
    private String rarity;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    @Column(name = "issued_year", length = 4)
    private String issuedYear;

    @Column(name = "card_number", length = 50)
    private String cardNumber;

    public CardMetadata(CardSet cardSet, String name, String language,
                        String psaGrade, String rarity, String imagePath) {
        this.cardSet = cardSet;
        this.name = name;
        this.language = language;
        this.psaGrade = psaGrade;
        this.rarity = rarity;
        this.imagePath = imagePath;
    }
}
