package com.example.seaweedfs.controller;

import com.example.seaweedfs.model.UploadTokenResponse;
import com.example.seaweedfs.service.ClientUploadService;
import com.example.seaweedfs.service.ProcessorService;
import com.example.seaweedfs.service.ReaderService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Exposes the three roles as REST endpoints so the full permission matrix
 * can be exercised without a custom client.
 *
 * Typical happy-path flow:
 *   1. POST /upload/token?key=hello.txt → backend authorizes and returns {uploadUrl, token}
 *   2. Client POSTs file bytes directly to uploadUrl with Authorization: Bearer {token}
 *   3. GET  /processor/inbox            → confirm file appears (PROCESSOR view)
 *   4. POST /processor/process?key=hello.txt → copy inbox→processed (content uppercased)
 *   5. GET  /reader/processed           → confirm file appears (READER view)
 *   6. GET  /reader/processed/hello.txt → read content
 */
@RestController
public class DemoController {

    private final ClientUploadService clientUploadService;
    private final ProcessorService    processorService;
    private final ReaderService       readerService;

    public DemoController(
            ClientUploadService clientUploadService,
            ProcessorService processorService,
            ReaderService readerService) {
        this.clientUploadService = clientUploadService;
        this.processorService    = processorService;
        this.readerService       = readerService;
    }

    // ── CLIENT: issue single-use upload token ─────────────────────────────

    @PostMapping("/upload/token")
    public ResponseEntity<UploadTokenResponse> requestUploadToken(@RequestParam String key) {
        return ResponseEntity.ok(clientUploadService.issueUploadToken(key));
    }

    // ── PROCESSOR ─────────────────────────────────────────────────────────

    @GetMapping("/processor/inbox")
    public List<String> listInbox() {
        return processorService.listInbox();
    }

    @PostMapping("/processor/process")
    public ResponseEntity<String> process(@RequestParam String key) {
        return ResponseEntity.ok(processorService.processFile(key));
    }

    // ── READER ────────────────────────────────────────────────────────────

    @GetMapping("/reader/processed")
    public List<String> listProcessed() {
        return readerService.listProcessed();
    }

    // {*key} captures the rest of the path including slashes (e.g. "images/photo.png")
    @GetMapping("/reader/processed/{*key}")
    public ResponseEntity<byte[]> read(@PathVariable String key) {
        String trimmedKey = key.startsWith("/") ? key.substring(1) : key;
        byte[] data = readerService.readFile(trimmedKey);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }
}
