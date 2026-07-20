package com.mtgtwitch.extension.desktop;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Service
public class MtgoAccountPreferences {

    private final Path configPath;
    private volatile List<String> usernames;

    public MtgoAccountPreferences() {
        this(BridgeLocalConfigStore.CONFIG_PATH);
    }

    MtgoAccountPreferences(Path configPath) {
        this.configPath = configPath;
        this.usernames = configPath == null
                ? List.of()
                : BridgeLocalConfigStore.readMtgoUsernames(configPath);
    }

    public static MtgoAccountPreferences fixedForTests(Collection<String> usernames) {
        MtgoAccountPreferences preferences = new MtgoAccountPreferences(null);
        preferences.usernames = BridgeLocalConfigStore.normalizeUsernames(usernames);
        return preferences;
    }

    public List<String> usernames() {
        return usernames;
    }

    public boolean hasConfiguredUsernames() {
        return !usernames.isEmpty();
    }

    public boolean allowsUsername(String username) {
        if (!hasConfiguredUsernames()) {
            return true;
        }
        if (username == null || username.isBlank()) {
            return false;
        }

        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        return usernames.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedUsername::equals);
    }

    public synchronized List<String> updateUsernames(Collection<String> nextUsernames) throws IOException {
        List<String> normalizedUsernames = BridgeLocalConfigStore.normalizeUsernames(nextUsernames);
        if (configPath != null) {
            BridgeLocalConfigStore.writeMtgoUsernames(configPath, normalizedUsernames);
        }
        usernames = normalizedUsernames;
        return usernames;
    }
}
