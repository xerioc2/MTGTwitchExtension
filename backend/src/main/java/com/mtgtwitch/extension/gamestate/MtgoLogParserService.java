package com.mtgtwitch.extension.gamestate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MtgoLogParserService {

    private static final Logger log = LoggerFactory.getLogger(MtgoLogParserService.class);

    private static final Pattern[] MOVE_PATTERNS = {
            Pattern.compile("(?i)^(.+?)\\s+(?:is\\s+)?(?:put|moved|returned)\\s+(?:from|out of)\\s+(?:the\\s+)?(.+?)\\s+(?:to|into|onto)\\s+(?:the\\s+)?(.+?)[\\.!]?$")
    };

    private static final Pattern[] REVERSED_MOVE_PATTERNS = {
            Pattern.compile("(?i)^(.+?)\\s+(?:is\\s+)?(?:put|moved|returned)\\s+(?:to|into|onto)\\s+(?:the\\s+)?(.+?)\\s+from\\s+(?:the\\s+)?(.+?)[\\.!]?$")
    };

    private static final Pattern[] ACTOR_MOVE_PATTERNS = {
            Pattern.compile("(?i)^.+?\\s+(?:puts|moves|returns)\\s+(.+)\\s+(?:from|out of)\\s+(?:the\\s+)?(.+?)\\s+(?:to|into|onto)\\s+(?:the\\s+)?(.+?)[\\.!]?$")
    };

    private static final Pattern[] ACTOR_REVERSED_MOVE_PATTERNS = {
            Pattern.compile("(?i)^.+?\\s+(?:puts|moves|returns)\\s+(.+)\\s+(?:to|into|onto)\\s+(?:the\\s+)?(.+?)\\s+from\\s+(?:the\\s+)?(.+?)[\\.!]?$")
    };

    private static final Pattern[] DESTINATION_PATTERNS = {
            Pattern.compile("(?i)^(.+?)\\s+(?:is\\s+)?(?:put|moved|returned)\\s+(?:to|into|onto)\\s+(?:the\\s+)?(.+?)[\\.!]?$"),
            Pattern.compile("(?i)^(.+?)\\s+(?:enters|entered)\\s+(?:the\\s+)?(.+?)[\\.!]?$"),
            Pattern.compile("(?i)^.+?\\s+(?:puts|moves|returns)\\s+(.+)\\s+(?:to|into|onto)\\s+(?:the\\s+)?(.+?)[\\.!]?$"),
            Pattern.compile("(?i)^(.+?)\\s+(?:is\\s+)?exiled[\\.!]?$"),
            Pattern.compile("(?i)^(.+?)\\s+(?:is\\s+)?discarded[\\.!]?$")
    };

    private static final Pattern PLAYER_ACTION_PATTERN = Pattern.compile(
            "(?i)^.+?\\s+(casts|plays|discards|exiles|reveals|draws)\\s+(.+?)[\\.!]?$"
    );

    private static final Pattern TWITCH_DECK_PATTERN = Pattern.compile(
            "^\\d{2}:\\d{2}:\\d{2}\\s+\\[INF]\\s+\\(Twitch Info\\|Username:\\s+.*?\\s+Deck Used in Game ID:\\s+(\\d+)\\)\\s+(\\[.*])$"
    );

    private static final Pattern GAME_STATUS_PATTERN = Pattern.compile(
            "^\\d{2}:\\d{2}:\\d{2}\\s+\\[INF]\\s+\\(Twitch Info\\|Game Play Status Update for Game ID:\\s+(\\d+),\\s+Match ID:\\s+(\\d+),\\s+Event ID:\\s+(\\d+)\\)\\s+(\\{.*})$"
    );

    private final GameStateService gameStateService;
    private final ObjectMapper objectMapper;

    public MtgoLogParserService(GameStateService gameStateService, ObjectMapper objectMapper) {
        this.gameStateService = gameStateService;
        this.objectMapper = objectMapper;
    }

    public Optional<CardZoneEvent> parseAndApply(String rawLine) {
        Optional<DeckCatalogEvent> deckCatalogEvent = parseDeckCatalogEvent(rawLine);
        if (deckCatalogEvent.isPresent()) {
            GameState gameState = gameStateService.updateDeckCatalogIds(deckCatalogEvent.get());
            log.info(
                    "Applied MTGO deck catalog event: gameId={}, catalogIds={}",
                    gameState.gameId(),
                    gameState.deckCatalogIds().size()
            );
            return Optional.empty();
        }

        Optional<GameStatusEvent> gameStatusEvent = parseGameStatusEvent(rawLine);
        if (gameStatusEvent.isPresent()) {
            GameState gameState = gameStateService.apply(gameStatusEvent.get());
            log.info(
                    "Applied MTGO game status update: gameId={}, players={}, hand={}, battlefield={}, graveyard={}, exile={}",
                    gameState.gameId(),
                    gameState.players().size(),
                    gameState.handCards().size(),
                    gameState.battlefieldCards().size(),
                    gameState.graveyardCards().size(),
                    gameState.exileCards().size()
            );
            return Optional.empty();
        }

        Optional<CardZoneEvent> event = parse(rawLine);
        event.ifPresent(cardZoneEvent -> {
            GameState gameState = gameStateService.apply(cardZoneEvent);
            log.info(
                    "Applied MTGO zone event: '{}' -> {}. Zone counts: hand={}, battlefield={}, graveyard={}, exile={}",
                    cardZoneEvent.cardName(),
                    cardZoneEvent.destinationZone(),
                    gameState.hand().size(),
                    gameState.battlefield().size(),
                    gameState.graveyard().size(),
                    gameState.exile().size()
            );
        });

        return event;
    }

    public Optional<DeckCatalogEvent> parseDeckCatalogEvent(String rawLine) {
        Matcher matcher = TWITCH_DECK_PATTERN.matcher(rawLine.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        long gameId = Long.parseLong(matcher.group(1));
        String deckJson = matcher.group(2);

        try {
            DeckCatalogEntry[] entries = objectMapper.readValue(deckJson, DeckCatalogEntry[].class);
            return Optional.of(new DeckCatalogEvent(
                    gameId,
                    Arrays.stream(entries)
                            .map(DeckCatalogEntry::catalogId)
                            .toList(),
                    rawLine
            ));
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse MTGO Twitch deck catalog line for gameId={}.", gameId, exception);
            return Optional.empty();
        }
    }

    public Optional<GameStatusEvent> parseGameStatusEvent(String rawLine) {
        Matcher matcher = GAME_STATUS_PATTERN.matcher(rawLine.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        long gameId = Long.parseLong(matcher.group(1));
        long matchId = Long.parseLong(matcher.group(2));
        long eventId = Long.parseLong(matcher.group(3));
        String statusJson = matcher.group(4);

        try {
            GameStatusPayload payload = objectMapper.readValue(statusJson, GameStatusPayload.class);
            return Optional.of(new GameStatusEvent(
                    gameId,
                    matchId,
                    eventId,
                    payload.players().stream()
                            .map(player -> new PlayerState(
                                    player.id(),
                                    player.name(),
                                    player.libraryCount(),
                                    player.handCount(),
                                    player.life()
                            ))
                            .toList(),
                    payload.cards().stream()
                            .map(card -> new GameCard(
                                    card.id(),
                                    card.catalogId(),
                                    card.zone(),
                                    card.actualZone(),
                                    card.owner(),
                                    card.controller()
                            ))
                            .toList(),
                    rawLine
            ));
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse MTGO game status update line for gameId={}.", gameId, exception);
            return Optional.empty();
        }
    }

    public Optional<CardZoneEvent> parse(String rawLine) {
        String normalizedLine = normalizeLine(rawLine);
        if (normalizedLine.isBlank()) {
            return Optional.empty();
        }

        for (Pattern pattern : MOVE_PATTERNS) {
            Matcher matcher = pattern.matcher(normalizedLine);
            if (matcher.matches()) {
                return buildEvent(matcher.group(1), matcher.group(2), matcher.group(3), rawLine);
            }
        }

        for (Pattern pattern : REVERSED_MOVE_PATTERNS) {
            Matcher matcher = pattern.matcher(normalizedLine);
            if (matcher.matches()) {
                return buildEvent(matcher.group(1), matcher.group(3), matcher.group(2), rawLine);
            }
        }

        for (Pattern pattern : ACTOR_MOVE_PATTERNS) {
            Matcher matcher = pattern.matcher(normalizedLine);
            if (matcher.matches()) {
                return buildEvent(matcher.group(1), matcher.group(2), matcher.group(3), rawLine);
            }
        }

        for (Pattern pattern : ACTOR_REVERSED_MOVE_PATTERNS) {
            Matcher matcher = pattern.matcher(normalizedLine);
            if (matcher.matches()) {
                return buildEvent(matcher.group(1), matcher.group(3), matcher.group(2), rawLine);
            }
        }

        for (Pattern pattern : DESTINATION_PATTERNS) {
            Matcher matcher = pattern.matcher(normalizedLine);
            if (matcher.matches()) {
                String cardName = cleanCardName(matcher.group(1));
                Zone destinationZone = destinationZoneForPattern(pattern, matcher);

                if (isLikelyCardName(cardName) && destinationZone != null) {
                    return Optional.of(new CardZoneEvent(cardName, null, destinationZone, rawLine));
                }
            }
        }

        return parsePlayerAction(normalizedLine, rawLine);
    }

    private Optional<CardZoneEvent> buildEvent(String cardNameText, String sourceZoneText, String destinationZoneText, String rawLine) {
        String cardName = cleanCardName(cardNameText);
        Optional<Zone> sourceZone = Zone.fromLogText(sourceZoneText);
        Optional<Zone> destinationZone = Zone.fromLogText(destinationZoneText);

        if (isLikelyCardName(cardName) && destinationZone.isPresent()) {
            return Optional.of(new CardZoneEvent(cardName, sourceZone.orElse(null), destinationZone.get(), rawLine));
        }

        return Optional.empty();
    }

    private Optional<CardZoneEvent> parsePlayerAction(String normalizedLine, String rawLine) {
        Matcher matcher = PLAYER_ACTION_PATTERN.matcher(normalizedLine);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String action = matcher.group(1).toLowerCase(Locale.ROOT);
        String cardName = cleanCardName(matcher.group(2));

        if (!isLikelyCardName(cardName)) {
            return Optional.empty();
        }

        return switch (action) {
            case "casts" -> Optional.of(new CardZoneEvent(cardName, Zone.HAND, Zone.BATTLEFIELD, rawLine));
            case "plays" -> Optional.of(new CardZoneEvent(cardName, Zone.HAND, Zone.BATTLEFIELD, rawLine));
            case "discards" -> Optional.of(new CardZoneEvent(cardName, Zone.HAND, Zone.GRAVEYARD, rawLine));
            case "exiles" -> Optional.of(new CardZoneEvent(cardName, null, Zone.EXILE, rawLine));
            case "draws", "reveals" -> Optional.of(new CardZoneEvent(cardName, null, Zone.HAND, rawLine));
            default -> Optional.empty();
        };
    }

    private Zone destinationZoneForPattern(Pattern pattern, Matcher matcher) {
        String patternText = pattern.pattern().toLowerCase(Locale.ROOT);
        if (patternText.contains("exiled")) {
            return Zone.EXILE;
        }
        if (patternText.contains("discarded")) {
            return Zone.GRAVEYARD;
        }

        return Zone.fromLogText(matcher.group(2)).orElse(null);
    }

    private String normalizeLine(String rawLine) {
        return rawLine
                .replaceAll("^\\[[^]]+]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanCardName(String cardName) {
        return cardName
                .replaceAll("^\"|\"$", "")
                .replaceAll("^'|'$", "")
                .replaceAll("\\s+\\(.*?\\)$", "")
                .replaceFirst("(?i)^(a|an|the)\\s+", "")
                .trim();
    }

    private boolean isLikelyCardName(String cardName) {
        if (cardName.isBlank()) {
            return false;
        }

        String lowerCaseCardName = cardName.toLowerCase(Locale.ROOT);
        return !lowerCaseCardName.equals("you")
                && !lowerCaseCardName.equals("opponent")
                && !lowerCaseCardName.equals("card")
                && !lowerCaseCardName.equals("a card")
                && !lowerCaseCardName.startsWith("player ");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeckCatalogEntry(@JsonProperty("CatalogId") int catalogId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GameStatusPayload(
            @JsonProperty("Players") List<GameStatusPlayer> players,
            @JsonProperty("Cards") List<GameStatusCard> cards
    ) {
        private GameStatusPayload {
            players = players == null ? List.of() : players;
            cards = cards == null ? List.of() : cards;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GameStatusPlayer(
            @JsonProperty("Id") int id,
            @JsonProperty("Name") String name,
            @JsonProperty("LibraryCount") int libraryCount,
            @JsonProperty("HandCount") int handCount,
            @JsonProperty("Life") int life
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GameStatusCard(
            @JsonProperty("Id") int id,
            @JsonProperty("CatalogID") int catalogId,
            @JsonProperty("Zone") String zone,
            @JsonProperty("ActualZone") String actualZone,
            @JsonProperty("Owner") int owner,
            @JsonProperty("Controller") int controller
    ) {
    }
}
