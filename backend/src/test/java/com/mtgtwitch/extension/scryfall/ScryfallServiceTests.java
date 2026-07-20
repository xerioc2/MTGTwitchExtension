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
                "https://cards.scryfall.io/normal/front/lightning-bolt.jpg",
                false
        ));
        assertThat(secondResult.get(78632)).isSameAs(firstResult.get(78632));
        server.verify();
    }

    @Test
    void returnsEmptyWhenMtgoCatalogIdDoesNotResolveAndDoesNotTryMultiverse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ScryfallService service = new ScryfallService(restTemplate, Duration.ZERO);

        server.expect(once(), requestTo("https://api.scryfall.com/cards/mtgo/125873"))
                .andRespond(withResourceNotFound());
        server.expect(once(), requestTo("https://api.scryfall.com/cards/mtgo/125872"))
                .andRespond(withResourceNotFound());
        server.expect(once(), requestTo("https://api.scryfall.com/cards/mtgo/125871"))
                .andRespond(withResourceNotFound());

        assertThat(service.fetchCard(125873)).isEmpty();
        server.verify();
    }

    @Test
    void infersModalDfcBackFaceFromNearbyFrontFaceAndCachesIt() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ScryfallService service = new ScryfallService(restTemplate, Duration.ZERO);

        server.expect(once(), requestTo("https://api.scryfall.com/cards/mtgo/126503"))
                .andRespond(withResourceNotFound());
        server.expect(once(), requestTo("https://api.scryfall.com/cards/mtgo/126502"))
                .andRespond(withResourceNotFound());
        server.expect(once(), requestTo("https://api.scryfall.com/cards/mtgo/126501"))
                .andRespond(withSuccess("""
                        {
                          "name": "Witch Enchanter // Witch-Blessed Meadow",
                          "layout": "modal_dfc",
                          "card_faces": [
                            {
                              "name": "Witch Enchanter",
                              "image_uris": {
                                "normal": "https://cards.scryfall.io/normal/front/witch-enchanter.jpg"
                              },
                              "oracle_text": "When Witch Enchanter enters, destroy target artifact or enchantment an opponent controls.",
                              "mana_cost": "{3}{W}",
                              "type_line": "Creature - Human Warlock"
                            },
                            {
                              "name": "Witch-Blessed Meadow",
                              "image_uris": {
                                "normal": "https://cards.scryfall.io/normal/back/witch-blessed-meadow.jpg"
                              },
                              "oracle_text": "Witch-Blessed Meadow enters tapped. {T}: Add {W}.",
                              "mana_cost": "",
                              "type_line": "Land"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ScryfallCard firstResult = service.fetchCard(126503).orElseThrow();
        ScryfallCard secondResult = service.fetchCard(126503).orElseThrow();

        assertThat(firstResult).isEqualTo(new ScryfallCard(
                126503,
                "Witch-Blessed Meadow",
                "Land",
                "",
                "Witch-Blessed Meadow enters tapped. {T}: Add {W}.",
                "https://cards.scryfall.io/normal/back/witch-blessed-meadow.jpg",
                true
        ));
        assertThat(secondResult).isSameAs(firstResult);
        server.verify();
    }

    @Test
    void doesNotInferBackFaceFromNormalNeighborCard() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ScryfallService service = new ScryfallService(restTemplate, Duration.ZERO);

        server.expect(once(), requestTo("https://api.scryfall.com/cards/mtgo/146993"))
                .andRespond(withResourceNotFound());
        server.expect(once(), requestTo("https://api.scryfall.com/cards/mtgo/146992"))
                .andRespond(withResourceNotFound());
        server.expect(once(), requestTo("https://api.scryfall.com/cards/mtgo/146991"))
                .andRespond(withSuccess("""
                        {
                          "name": "Serra Sphinx",
                          "layout": "normal",
                          "image_uris": {
                            "normal": "https://cards.scryfall.io/normal/front/serra-sphinx.jpg"
                          },
                          "oracle_text": "Flying, vigilance",
                          "mana_cost": "{4}{U}",
                          "type_line": "Creature - Sphinx"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.fetchCard(146993)).isEmpty();
        server.verify();
    }
}
