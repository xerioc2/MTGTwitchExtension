package com.mtgtwitch.extension.api;

import com.mtgtwitch.extension.desktop.MtgoAccountPreferences;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MtgoAccountsController {

    private final MtgoAccountPreferences mtgoAccountPreferences;

    public MtgoAccountsController(MtgoAccountPreferences mtgoAccountPreferences) {
        this.mtgoAccountPreferences = mtgoAccountPreferences;
    }

    @GetMapping("/mtgo-accounts")
    public MtgoAccountsResponse mtgoAccounts() {
        return new MtgoAccountsResponse(mtgoAccountPreferences.usernames());
    }

    @PutMapping("/mtgo-accounts")
    public ResponseEntity<MtgoAccountsResponse> updateMtgoAccounts(@RequestBody MtgoAccountsRequest request) throws IOException {
        List<String> usernames = mtgoAccountPreferences.updateUsernames(request.usernames());
        return ResponseEntity.ok(new MtgoAccountsResponse(usernames));
    }

    public record MtgoAccountsRequest(List<String> usernames) {
    }

    public record MtgoAccountsResponse(List<String> usernames) {
    }
}
