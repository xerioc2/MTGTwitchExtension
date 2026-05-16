package com.mtgtwitch.extension.scryfall;

public record ScryfallCard(
        int catalogId,
        String name,
        String typeLine,
        String manaCost,
        String oracleText,
        String imageUrl
) {
}
