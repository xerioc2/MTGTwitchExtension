package com.mtgtwitch.extension.scryfall;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

@Component
public class TokenOverrideCatalog {

    private static final String RESOURCE_PATH = "/token-overrides.json";

    private final Map<Integer, TokenOverride> overrides;

    @Autowired
    public TokenOverrideCatalog(ObjectMapper objectMapper) {
        this.overrides = loadOverrides(objectMapper);
    }

    TokenOverrideCatalog(Map<Integer, TokenOverride> overrides) {
        this.overrides = Map.copyOf(overrides);
    }

    public Optional<ScryfallCard> find(int catalogId) {
        TokenOverride override = overrides.get(catalogId);
        if (override == null) {
            return Optional.empty();
        }

        return Optional.of(new ScryfallCard(
                catalogId,
                override.name(),
                override.typeLine(),
                override.manaCost(),
                override.oracleText(),
                override.imageUrl(),
                false,
                true
        ));
    }

    private Map<Integer, TokenOverride> loadOverrides(ObjectMapper objectMapper) {
        try (InputStream stream = TokenOverrideCatalog.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                return Map.of();
            }

            return objectMapper.readValue(stream, new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load MTGO token override catalog.", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenOverride(
            @JsonProperty("name") String name,
            @JsonProperty("typeLine") String typeLine,
            @JsonProperty("manaCost") String manaCost,
            @JsonProperty("oracleText") String oracleText,
            @JsonProperty("imageUrl") String imageUrl,
            @JsonProperty("token") boolean token
    ) {
    }
}
