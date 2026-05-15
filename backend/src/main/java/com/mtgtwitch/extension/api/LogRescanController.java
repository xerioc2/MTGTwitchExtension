package com.mtgtwitch.extension.api;

import com.mtgtwitch.extension.log.LogWatchStatus;
import com.mtgtwitch.extension.log.MtgoLogWatcherService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LogRescanController {

    private final MtgoLogWatcherService mtgoLogWatcherService;

    public LogRescanController(MtgoLogWatcherService mtgoLogWatcherService) {
        this.mtgoLogWatcherService = mtgoLogWatcherService;
    }

    @PostMapping("/rescan-log")
    public ResponseEntity<LogWatchStatus> rescanLog() {
        LogWatchStatus status = mtgoLogWatcherService.rescan();
        if (!status.watching()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(status);
        }

        return ResponseEntity.ok(status);
    }
}
