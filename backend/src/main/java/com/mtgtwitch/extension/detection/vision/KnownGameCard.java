package com.mtgtwitch.extension.detection.vision;

import com.mtgtwitch.extension.gamestate.GameCard;
import com.mtgtwitch.extension.scryfall.ScryfallCard;

public record KnownGameCard(GameCard gameCard, ScryfallCard details, String zone) {
}
