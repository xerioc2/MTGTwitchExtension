package com.mtgtwitch.extension.api;

import com.mtgtwitch.extension.scryfall.ScryfallCard;
import com.mtgtwitch.extension.scryfall.ScryfallService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class ScryfallController {

    private final ScryfallService scryfallService;

    public ScryfallController(ScryfallService scryfallService) {
        this.scryfallService = scryfallService;
    }

    @GetMapping("/{catalogId}")
    public ResponseEntity<ScryfallCard> getCard(@PathVariable int catalogId) {
        return scryfallService.fetchCard(catalogId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
