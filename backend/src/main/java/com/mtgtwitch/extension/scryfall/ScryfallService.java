package com.mtgtwitch.extension.scryfall;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScryfallService {

    private static final Logger log = LoggerFactory.getLogger(ScryfallService.class);
    private static final String SCRYFALL_MTGO_CARD_URL = "https://api.scryfall.com/cards/mtgo/{catalogId}";

    private final RestTemplate restTemplate;
    private final Duration requestDelay;
    private final RemoteCardResolverClient remoteCardResolverClient;
    private final TokenOverrideCatalog tokenOverrideCatalog;
    private final Map<Integer, ScryfallCard> cache = new ConcurrentHashMap<>();
    private final Set<Integer> unresolvedCatalogIdsLogged = ConcurrentHashMap.newKeySet();
    private long lastRequestNanos;

    @Autowired
    public ScryfallService(
            RestTemplateBuilder restTemplateBuilder,
            RemoteCardResolverClient remoteCardResolverClient,
            TokenOverrideCatalog tokenOverrideCatalog
    ) {
        this(restTemplateBuilder.build(), Duration.ofMillis(100), remoteCardResolverClient, tokenOverrideCatalog);
    }

    ScryfallService(RestTemplate restTemplate, Duration requestDelay) {
        this(restTemplate, requestDelay, RemoteCardResolverClient.disabled(), new TokenOverrideCatalog(Map.of()));
    }

    ScryfallService(
            RestTemplate restTemplate,
            Duration requestDelay,
            RemoteCardResolverClient remoteCardResolverClient,
            TokenOverrideCatalog tokenOverrideCatalog
    ) {
        this.restTemplate = restTemplate;
        this.requestDelay = requestDelay;
        this.remoteCardResolverClient = remoteCardResolverClient;
        this.tokenOverrideCatalog = tokenOverrideCatalog;
    }

    public synchronized Map<Integer, ScryfallCard> fetchCards(List<Integer> catalogIds) {
        Map<Integer, ScryfallCard> cardsByCatalogId = new LinkedHashMap<>();

        for (Integer catalogId : catalogIds.stream().distinct().toList()) {
            fetchCard(catalogId).ifPresent(card -> cardsByCatalogId.put(catalogId, card));
        }

        return cardsByCatalogId;
    }

    public synchronized Optional<ScryfallCard> fetchCard(int catalogId) {
        if (catalogId <= 0) {
            return Optional.empty();
        }

        ScryfallCard cachedCard = cache.get(catalogId);
        if (cachedCard != null) {
            return Optional.of(cachedCard);
        }

        Optional<ScryfallCard> fetchedCard = remoteCardResolverClient.resolve(catalogId)
                .or(() -> tokenOverrideCatalog.find(catalogId))
                .or(() -> fetchApiCard(catalogId)
                        .map(apiCard -> toCard(catalogId, apiCard, false))
                        .or(() -> inferBackFace(catalogId)));
        fetchedCard.ifPresent(card -> cache.put(catalogId, card));
        if (fetchedCard.isEmpty() && unresolvedCatalogIdsLogged.add(catalogId)) {
            log.info("Unresolved MTGO catalog id {}; add a token override if this is a token.", catalogId);
        }
        return fetchedCard;
    }

    private Optional<ScryfallCard> inferBackFace(int catalogId) {
        for (int offset = 1; offset <= 2; offset++) {
            int neighborCatalogId = catalogId - offset;
            if (neighborCatalogId <= 0) {
                continue;
            }

            Optional<ScryfallApiCard> neighborCard = fetchApiCard(neighborCatalogId);
            if (neighborCard.isEmpty()) {
                continue;
            }

            ScryfallApiCard apiCard = neighborCard.get();
            if (!isDoubleFacedLayout(apiCard.layout())) {
                return Optional.empty();
            }

            return backFaceCard(catalogId, apiCard);
        }

        return Optional.empty();
    }

    private Optional<ScryfallApiCard> fetchApiCard(int catalogId) {
        throttleRequests();

        try {
            ScryfallApiCard apiCard = restTemplate.getForObject(
                    SCRYFALL_MTGO_CARD_URL,
                    ScryfallApiCard.class,
                    catalogId
            );

            if (apiCard == null) {
                return Optional.empty();
            }

            return Optional.of(apiCard);
        } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new ScryfallServiceException("Failed to fetch Scryfall card for MTGO catalog id " + catalogId, exception);
        }
    }

    private ScryfallCard toCard(int catalogId, ScryfallApiCard apiCard, boolean inferredBackFace) {
        return new ScryfallCard(
                catalogId,
                apiCard.name(),
                apiCard.typeLine(),
                apiCard.manaCost(),
                apiCard.oracleText(),
                normalImageUrl(apiCard.imageUris()),
                inferredBackFace
        );
    }

    private Optional<ScryfallCard> backFaceCard(int catalogId, ScryfallApiCard apiCard) {
        if (apiCard.cardFaces() == null || apiCard.cardFaces().size() < 2) {
            return Optional.empty();
        }

        ScryfallCardFace backFace = apiCard.cardFaces().get(1);
        return Optional.of(new ScryfallCard(
                catalogId,
                backFace.name(),
                backFace.typeLine(),
                backFace.manaCost(),
                backFace.oracleText(),
                normalImageUrl(backFace.imageUris(), apiCard.imageUris()),
                true
        ));
    }

    private boolean isDoubleFacedLayout(String layout) {
        if (layout == null) {
            return false;
        }

        String normalizedLayout = layout.toLowerCase(java.util.Locale.ROOT);
        return "modal_dfc".equals(normalizedLayout) || "transform".equals(normalizedLayout);
    }

    private String normalImageUrl(ScryfallImageUris... imageUris) {
        for (ScryfallImageUris imageUri : imageUris) {
            if (imageUri != null && imageUri.normal() != null) {
                return imageUri.normal();
            }
        }

        return null;
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
            @JsonProperty("type_line") String typeLine,
            @JsonProperty("layout") String layout,
            @JsonProperty("card_faces") List<ScryfallCardFace> cardFaces
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ScryfallCardFace(
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
