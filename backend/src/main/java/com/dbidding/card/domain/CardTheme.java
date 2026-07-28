package com.dbidding.card.domain;

public final class CardTheme {
    private CardTheme() {
    }

    public static String from(CardMetadata card) {
        String rarity = card.getRarity() == null ? "" : card.getRarity().toLowerCase();
        if (rarity.contains("water")) return "water";
        if (rarity.contains("dark")) return "dark";
        if (rarity.contains("sketch")) return "sketch";
        if (rarity.contains("multi") || rarity.contains("rainbow")) return "multi";
        return "gold";
    }
}
