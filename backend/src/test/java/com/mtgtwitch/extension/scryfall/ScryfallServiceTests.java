package com.mtgtwitch.extension.scryfall;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ScryfallServiceTests {

    @Test
    void fetchesCardsByMtgoCatalogIdAndCachesResponses() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ScryfallService service = new ScryfallService(restTemplate, Duration.ZERO);

        server.expect(once(), requestTo("https://api.scryfall.com/cards/mtgo/78632"))
                .andRespond(withSuccess("""
                        {
                          "name": "Lightning Bolt",
                          "image_uris": {
                            "normal": "https://cards.scryfall.io/normal/front/lightning-bolt.jpg"
                          },
                          "oracle_text": "Lightning Bolt deals 3 damage to any target.",
                          "mana_cost": "{R}",
                          "type_line": "Instant"
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<Integer, ScryfallCard> firstResult = service.fetchCards(List.of(78632, 78632));
        Map<Integer, ScryfallCard> secondResult = service.fetchCards(List.of(78632));

        assertThat(firstResult).containsOnlyKeys(78632);
        assertThat(firstResult.get(78632)).isEqualTo(new ScryfallCard(
                78632,
                "Lightning Bolt",
                "Instant",
                "{R}",
                "Lightning Bolt deals 3 damage to any target.",
                "https://cards.scryfall.io/normal/front/lightning-bolt.jpg"
        ));
        assertThat(secondResult.get(78632)).isSameAs(firstResult.get(78632));
        server.verify();
    }

    @Test
    void returnsEmptyWhenScryfallDoesNotFindCatalogId() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ScryfallService service = new ScryfallService(restTemplate, Duration.ZERO);

        server.expect(once(), requestTo("https://api.scryfall.com/cards/mtgo/999999"))
                .andRespond(withResourceNotFound());
        server.expect(once(), requestTo("https://api.scryfall.com/cards/multiverse/999999"))
                .andRespond(withResourceNotFound());

        assertThat(service.fetchCard(999999)).isEmpty();
        server.verify();
    }

    @Test
    void fallsBackToMultiverseWhenMtgoLookupDoesNotResolve() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ScryfallService service = new ScryfallService(restTemplate, Duration.ZERO);

        server.expect(once(), requestTo("https://api.scryfall.com/cards/mtgo/133248"))
                .andRespond(withResourceNotFound());
        server.expect(once(), requestTo("https://api.scryfall.com/cards/multiverse/133248"))
                .andRespond(withSuccess("""
                        {
                          "name": "Tarmogoyf",
                          "image_uris": {
                            "normal": "https://cards.scryfall.io/normal/front/tarmogoyf.jpg"
                          },
                          "oracle_text": "Tarmogoyf's power is equal to the number of card types among cards in all graveyards and its toughness is equal to that number plus 1.",
                          "mana_cost": "{1}{G}",
                          "type_line": "Creature — Lhurgoyf"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.fetchCard(133248)).hasValue(new ScryfallCard(
                133248,
                "Tarmogoyf",
                "Creature — Lhurgoyf",
                "{1}{G}",
                "Tarmogoyf's power is equal to the number of card types among cards in all graveyards and its toughness is equal to that number plus 1.",
                "https://cards.scryfall.io/normal/front/tarmogoyf.jpg"
        ));
        server.verify();
    }
}
