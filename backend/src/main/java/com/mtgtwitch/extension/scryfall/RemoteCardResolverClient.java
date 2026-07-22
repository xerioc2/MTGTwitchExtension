package com.mtgtwitch.extension.scryfall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;

@Service
public class RemoteCardResolverClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteCardResolverClient.class);

    private final RestTemplate restTemplate;
    private String cardResolverFunctionUrl;
    private String bridgePublishToken;

    @Autowired
    public RemoteCardResolverClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${supabase.card-resolver-function-url:}") String cardResolverFunctionUrl,
            @Value("${supabase.bridge-publish-token:}") String bridgePublishToken
    ) {
        this(
                restTemplateBuilder
                        .setConnectTimeout(Duration.ofSeconds(3))
                        .setReadTimeout(Duration.ofSeconds(6))
                        .build(),
                cardResolverFunctionUrl,
                bridgePublishToken
        );
    }

    RemoteCardResolverClient(RestTemplate restTemplate, String cardResolverFunctionUrl, String bridgePublishToken) {
        this.restTemplate = restTemplate;
        this.cardResolverFunctionUrl = cardResolverFunctionUrl;
        this.bridgePublishToken = bridgePublishToken;
    }

    static RemoteCardResolverClient disabled() {
        return new RemoteCardResolverClient(new RestTemplate(), "", "");
    }

    public synchronized void configure(String cardResolverFunctionUrl, String bridgePublishToken) {
        this.cardResolverFunctionUrl = cardResolverFunctionUrl;
        this.bridgePublishToken = bridgePublishToken;
        log.info(
                "Configured remote card resolver: {}.",
                StringUtils.hasText(cardResolverFunctionUrl) ? "enabled" : "disabled"
        );
    }

    public synchronized Optional<ScryfallCard> resolve(int catalogId) {
        if (!StringUtils.hasText(cardResolverFunctionUrl) || !StringUtils.hasText(bridgePublishToken)) {
            return Optional.empty();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bridgePublishToken);

        try {
            ScryfallCard card = restTemplate.postForObject(
                    cardResolverFunctionUrl,
                    new HttpEntity<>(new ResolveCardRequest(catalogId), headers),
                    ScryfallCard.class
            );

            return Optional.ofNullable(card);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest exception) {
            return Optional.empty();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
            log.warn("Remote card resolver rejected the bridge token.");
            return Optional.empty();
        } catch (RestClientException exception) {
            log.warn("Remote card resolver unavailable; falling back to local Scryfall lookup.", exception);
            return Optional.empty();
        }
    }

    private record ResolveCardRequest(int catalogId) {
    }
}
