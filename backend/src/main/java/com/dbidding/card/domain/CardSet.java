package com.dbidding.card.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "card_sets")
public class CardSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(unique = true, length = 50)
    private String code;

    protected CardSet() {
    }

    public CardSet(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
