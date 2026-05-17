package com.mtgtwitch.extension.scryfall;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScryfallService {

    private static final String SCRYFALL_MTGO_CARD_URL = "https://api.scryfall.com/cards/mtgo/{catalogId}";
    private static final String SCRYFALL_MULTIVERSE_CARD_URL = "https://api.scryfall.com/cards/multiverse/{catalogId}";

    private final RestTemplate restTemplate;
    private final Duration requestDelay;
    private final Map<Integer, ScryfallCard> cache = new ConcurrentHashMap<>();
    private long lastRequestNanos;

    @Autowired
    public ScryfallService(RestTemplateBuilder restTemplateBuilder) {
        this(restTemplateBuilder.build(), Duration.ofMillis(100));
    }

    ScryfallService(RestTemplate restTemplate, Duration requestDelay) {
        this.restTemplate = restTemplate;
        this.requestDelay = requestDelay;
    }

    public synchronized Map<Integer, ScryfallCard> fetchCards(List<Integer> catalogIds) {
        Map<Integer, ScryfallCard> cardsByCatalogId = new LinkedHashMap<>();

        for (Integer catalogId : catalogIds.stream().distinct().toList()) {
            fetchCard(catalogId).ifPresent(card -> cardsByCatalogId.put(catalogId, card));
        }

        return cardsByCatalogId;
    }

    public synchronized Optional<ScryfallCard> fetchCard(int catalogId) {
        ScryfallCard cachedCard = cache.get(catalogId);
        if (cachedCard != null) {
            return Optional.of(cachedCard);
        }

        Optional<ScryfallCard> fetchedCard = fetchCardFromScryfall(catalogId, SCRYFALL_MTGO_CARD_URL)
                .or(() -> fetchCardFromScryfall(catalogId, SCRYFALL_MULTIVERSE_CARD_URL));
        fetchedCard.ifPresent(card -> cache.put(catalogId, card));
        return fetchedCard;
    }

    private Optional<ScryfallCard> fetchCardFromScryfall(int catalogId, String url) {
        throttleRequests();

        try {
            ScryfallApiCard apiCard = restTemplate.getForObject(
                    url,
                    ScryfallApiCard.class,
                    catalogId
            );

            if (apiCard == null) {
                return Optional.empty();
            }

            return Optional.of(new ScryfallCard(
                    catalogId,
                    apiCard.name(),
                    apiCard.typeLine(),
                    apiCard.manaCost(),
                    apiCard.oracleText(),
                    apiCard.imageUris() == null ? null : apiCard.imageUris().normal()
            ));
        } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new ScryfallServiceException("Failed to fetch Scryfall card for MTGO catalog id " + catalogId, exception);
        }
    }

    private void throttleRequests() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRequestNanos;
        long delayNanos = requestDelay.toNanos();

        if (lastRequestNanos > 0 && elapsedNanos < delayNanos) {
            try {
                Thread.sleep(Duration.ofNanos(delayNanos - elapsedNanos).toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ScryfallServiceException("Interrupted while waiting for Scryfall rate limit delay.", exception);
            }
        }

        lastRequestNanos = System.nanoTime();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScryfallApiCard(
            @JsonProperty("name") String name,
            @JsonProperty("image_uris") ScryfallImageUris imageUris,
            @JsonProperty("oracle_text") String oracleText,
            @JsonProperty("mana_cost") String manaCost,
            @JsonProperty("type_line") String typeLine
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScryfallImageUris(@JsonProperty("normal") String normal) {
    }
}
