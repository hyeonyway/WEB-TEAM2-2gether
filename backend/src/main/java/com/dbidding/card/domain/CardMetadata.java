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
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_set_id", nullable = false)
    private CardSet cardSet;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "card_number", length = 50)
    private String cardNumber;

    @Column(length = 20)
    private String language;

    @Column(name = "psa_grade")
    private Integer psaGrade;

    @Column(length = 30)
    private String rarity;

    @Column(name = "favorite_count")
    private Integer favoriteCount;

    @Column(name = "reference_price")
    private Long referencePrice;

    @Column(name = "image_path", length = 500)
    private String imagePath;

    public CardMetadata(CardSet cardSet, String name, String cardNumber, String language,
                        Integer psaGrade, String rarity, Long referencePrice, String imagePath) {
        this.cardSet = cardSet;
        this.name = name;
        this.cardNumber = cardNumber;
        this.language = language;
        this.psaGrade = psaGrade;
        this.rarity = rarity;
        this.referencePrice = referencePrice;
        this.imagePath = imagePath;
        this.favoriteCount = 0;
    }
}
