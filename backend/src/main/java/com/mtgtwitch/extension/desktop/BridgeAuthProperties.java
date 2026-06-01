package com.mtgtwitch.extension.desktop;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BridgeAuthProperties {

    private final String twitchClientId;
    private final String issueBridgeTokenUrl;

    public BridgeAuthProperties(
            @Value("${twitch.client-id}") String twitchClientId,
            @Value("${supabase.issue-token-url}") String issueBridgeTokenUrl
    ) {
        this.twitchClientId = twitchClientId;
        this.issueBridgeTokenUrl = issueBridgeTokenUrl;
    }

    public String twitchClientId() {
        return twitchClientId;
    }

    public String issueBridgeTokenUrl() {
        return issueBridgeTokenUrl;
    }
}
