package com.example.seaweedfs.controller;

import com.example.seaweedfs.service.ClientUploadService;
import com.example.seaweedfs.service.ProcessorService;
import com.example.seaweedfs.service.ReaderService;
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

    public DemoController(ClientUploadService clientUploadService,
                          ProcessorService processorService,
                          ReaderService readerService) {
        this.clientUploadService = clientUploadService;
        this.processorService = processorService;
        this.readerService = readerService;
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

    /** Copies an object from inbox to processed. */
    @PostMapping("/process/{key}")
    public String process(@PathVariable String key) {
        return processorService.process(key);
    }

    // ── reader-service ────────────────────────────────────────────────────────

    /** Lists objects in processed (reader perspective). */
    @GetMapping("/processed")
    public List<String> listProcessed() {
        return readerService.listProcessed();
    }

    /** Returns the text content of an object in processed. */
    @GetMapping("/processed/{key}")
    public String readProcessed(@PathVariable String key) {
        return readerService.read(key);
    }
}
