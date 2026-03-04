package com.example.seaweedfs.controller;

import com.example.seaweedfs.service.ClientUploadService;
import com.example.seaweedfs.service.ProbeService;
import com.example.seaweedfs.service.ProcessorService;
import com.example.seaweedfs.service.ReaderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Exposes all ACL roles as REST endpoints so the full permission matrix
 * can be exercised without a custom client.
 *
 * Typical happy-path flow:
 *   1. GET  /demo/presign?key=hello.txt      → get a presigned PUT URL
 *   2. PUT  <presigned-url>  (body: any text) → upload via client-uploader identity
 *   3. GET  /demo/inbox                      → confirm file appears (processor view)
 *   4. POST /demo/process/hello.txt          → copy inbox→processed
 *   5. GET  /demo/processed                  → confirm file appears (reader view)
 *   6. GET  /demo/processed/hello.txt        → read content
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    private final ClientUploadService clientUploadService;
    private final ProcessorService processorService;
    private final ReaderService readerService;
    private final ProbeService probeService;

    public DemoController(ClientUploadService clientUploadService,
                          ProcessorService processorService,
                          ReaderService readerService,
                          ProbeService probeService) {
        this.clientUploadService = clientUploadService;
        this.processorService = processorService;
        this.readerService = readerService;
        this.probeService = probeService;
    }

    // ── client-uploader ───────────────────────────────────────────────────────

    /** Returns a presigned PUT URL valid for 15 minutes. Use it to upload directly to inbox. */
    @GetMapping("/presign")
    public String presign(@RequestParam String key) {
        return clientUploadService.generatePresignedPutUrl(key);
    }

    // ── processor-service ─────────────────────────────────────────────────────

    /** Lists objects currently in inbox (processor perspective). */
    @GetMapping("/inbox")
    public List<String> listInbox() {
        return processorService.listInbox();
    }

    /** Copies an object from inbox to processed. Key may contain path separators (e.g. folder/file.png). */
    @PostMapping("/process/**")
    public String process(HttpServletRequest request) {
        String key = request.getRequestURI().substring("/demo/process/".length());
        return processorService.process(key);
    }

    // ── reader-service ────────────────────────────────────────────────────────

    /** Lists objects in processed (reader perspective). */
    @GetMapping("/processed")
    public List<String> listProcessed() {
        return readerService.listProcessed();
    }

    /** Returns the raw bytes of an object in processed. Key may contain path separators. */
    @GetMapping("/processed/**")
    public ResponseEntity<byte[]> readProcessed(HttpServletRequest request) {
        String key = request.getRequestURI().substring("/demo/processed/".length());
        return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .body(readerService.read(key));
    }

    // ── probe: unauthorized access verification ───────────────────────────────

    /** reader-service (Read/List processed only) tries to GET from inbox — expects 403. */
    @GetMapping("/probe/reader-reads-inbox")
    public ResponseEntity<String> probeReaderReadsInbox(@RequestParam String key) {
        int status = probeService.readerReadsInbox(key);
        return ResponseEntity.status(status).body("SeaweedFS returned HTTP " + status);
    }

    /** processor-service (Read/List inbox, Write processed) tries to PUT to inbox — expects 403. */
    @PostMapping("/probe/processor-writes-inbox")
    public ResponseEntity<String> probeProcessorWritesInbox(@RequestParam String key) {
        int status = probeService.processorWritesInbox(key);
        return ResponseEntity.status(status).body("SeaweedFS returned HTTP " + status);
    }
}
