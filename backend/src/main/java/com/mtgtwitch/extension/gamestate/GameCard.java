package com.mtgtwitch.extension.gamestate;

public record GameCard(
        int id,
        int catalogId,
        String zone,
        String actualZone,
        int owner,
        int controller
) {
    public String displayName() {
        return "CatalogID " + catalogId;
    }
}
