package com.mtgtwitch.extension.log;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class LogDiscoveryProgress {

    private static final AtomicReference<Status> STATUS = new AtomicReference<>(Status.idle());

    private LogDiscoveryProgress() {
    }

    public static void beginScan() {
        STATUS.set(Status.scanning(0));
    }

    public static void candidateFound() {
        STATUS.updateAndGet(Status::withCandidateFound);
    }

    public static void complete(Path path) {
        STATUS.updateAndGet(current -> Status.complete(
                current.candidateCount(),
                path == null ? null : path.toString(),
                path == null ? "No MTGO log file was found." : path.toString(),
                path != null
        ));
    }

    public static void fail(String message) {
        STATUS.updateAndGet(current -> Status.complete(
                current.candidateCount(),
                null,
                message == null || message.isBlank() ? "Failed while scanning for MTGO logs." : message,
                false
        ));
    }

    public static Status snapshot() {
        return STATUS.get();
    }

    static void resetForTests() {
        STATUS.set(Status.idle());
    }

    public record Status(
            boolean scanning,
            int candidateCount,
            String path,
            String message,
            boolean watching
    ) {
        private static Status idle() {
            return new Status(false, 0, null, "", false);
        }

        private static Status scanning(int candidateCount) {
            return new Status(true, candidateCount, null, scanningMessage(candidateCount), false);
        }

        private static Status complete(int candidateCount, String path, String message, boolean watching) {
            return new Status(false, Math.max(0, candidateCount), path, message, watching);
        }

        private Status withCandidateFound() {
            return scanning(candidateCount + 1);
        }

        private static String scanningMessage(int candidateCount) {
            return "Scanning %d candidate %s..."
                    .formatted(candidateCount, candidateCount == 1 ? "log" : "logs");
        }
    }
}
