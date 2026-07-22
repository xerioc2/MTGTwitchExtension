package com.mtgtwitch.extension.scryfall;

public record ScryfallCard(
        int catalogId,
        String name,
        String typeLine,
        String manaCost,
        String oracleText,
        String imageUrl,
        boolean inferredBackFace,
        boolean token
) {
    public ScryfallCard(
            int catalogId,
            String name,
            String typeLine,
            String manaCost,
            String oracleText,
            String imageUrl,
            boolean inferredBackFace
    ) {
        this(catalogId, name, typeLine, manaCost, oracleText, imageUrl, inferredBackFace, false);
    }
}
