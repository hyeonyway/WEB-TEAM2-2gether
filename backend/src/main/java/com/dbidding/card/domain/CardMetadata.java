package com.dbidding.card.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "card_metadata")
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

    protected CardMetadata() {
    }

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

    public Long getId() { return id; }
    public CardSet getCardSet() { return cardSet; }
    public String getName() { return name; }
    public String getCardNumber() { return cardNumber; }
    public String getLanguage() { return language; }
    public Integer getPsaGrade() { return psaGrade; }
    public String getRarity() { return rarity; }
    public Integer getFavoriteCount() { return favoriteCount; }
    public Long getReferencePrice() { return referencePrice; }
    public String getImagePath() { return imagePath; }
}
