package com.example.seaweedfs.controller;

import com.example.seaweedfs.config.SeaweedfsProperties;
import com.example.seaweedfs.model.CallerRole;
import com.example.seaweedfs.service.FilerAuthorizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Probe endpoints for integration testing of authorization rules.
 * Each endpoint exercises a specific cross-role violation so that the 403
 * response from FilerAuthorizationService can be asserted in tests.
 *
 * These endpoints have no production purpose — they exist only to make
 * FilerAuthorizationService's deny paths observable via HTTP.
 */
@RestController
@RequestMapping("/probe")
public class ProbeController {

    private final FilerAuthorizationService auth;
    private final String                    inboxPath;

    public ProbeController(FilerAuthorizationService auth, SeaweedfsProperties props) {
        this.auth      = auth;
        this.inboxPath = props.getFiler().getBuckets().getInbox();
    }

    /**
     * READER role attempting to read from inbox.
     * FilerAuthorizationService must deny this — READER may only access processed.
     */
    @GetMapping("/reader-reads-inbox")
    public ResponseEntity<String> readerReadsInbox(@RequestParam String key) {
        auth.assertCanRead(CallerRole.READER, inboxPath + "/" + key);
        return ResponseEntity.ok("allowed"); // unreachable if auth is correct
    }

    /**
     * PROCESSOR role attempting to write to inbox.
     * FilerAuthorizationService must deny this — PROCESSOR may only write to processed.
     */
    @PostMapping("/processor-writes-inbox")
    public ResponseEntity<String> processorWritesInbox(@RequestParam String key) {
        auth.assertCanWrite(CallerRole.PROCESSOR, inboxPath + "/" + key);
        return ResponseEntity.ok("allowed"); // unreachable if auth is correct
    }
}
