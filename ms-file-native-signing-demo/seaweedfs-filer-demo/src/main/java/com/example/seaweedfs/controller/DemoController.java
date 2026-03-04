package com.example.seaweedfs.controller;

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
 *   1. POST /upload?key=hello.txt      (body: any text) → proxied upload via CLIENT role
 *   2. GET  /processor/inbox           → confirm file appears (PROCESSOR view)
 *   3. POST /processor/process?key=hello.txt → copy inbox→processed (content uppercased)
 *   4. GET  /reader/processed          → confirm file appears (READER view)
 *   5. GET  /reader/processed/hello.txt → read content
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

    // ── CLIENT: proxied upload ─────────────────────────────────────────────

    @PostMapping("/upload")
    public ResponseEntity<String> upload(
            @RequestParam String key,
            @RequestParam(defaultValue = "application/octet-stream") String contentType,
            @RequestBody byte[] data) {
        clientUploadService.upload(key, data, contentType);
        return ResponseEntity.ok("Uploaded to inbox: " + key);
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

    @GetMapping("/reader/processed/{key}")
    public ResponseEntity<byte[]> read(@PathVariable String key) {
        byte[] data = readerService.readFile(key);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }
}
