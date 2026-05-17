package com.mtgtwitch.extension.log;

public record LogWatchStatus(
        String path,
        boolean watching,
        String message
) {
}
