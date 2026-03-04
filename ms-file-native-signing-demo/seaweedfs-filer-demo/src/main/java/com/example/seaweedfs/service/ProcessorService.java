package com.example.seaweedfs.service;

import com.example.seaweedfs.client.FilerHttpClient;
import com.example.seaweedfs.config.SeaweedfsProperties;
import com.example.seaweedfs.model.CallerRole;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Uses the PROCESSOR role:
 *   - can read from inbox
 *   - can write to processed
 *   - cannot read from processed or write to inbox
 */
@Service
public class ProcessorService {

    private final FilerAuthorizationService auth;
    private final FilerHttpClient           filer;
    private final String                    inboxPath;
    private final String                    processedPath;

    public ProcessorService(
            FilerAuthorizationService auth,
            FilerHttpClient filer,
            SeaweedfsProperties props) {
        this.auth          = auth;
        this.filer         = filer;
        this.inboxPath     = props.getFiler().getBuckets().getInbox();
        this.processedPath = props.getFiler().getBuckets().getProcessed();
    }

    public List<String> listInbox() {
        auth.assertCanRead(CallerRole.PROCESSOR, inboxPath + "/listing");
        return filer.listDirectory(inboxPath + "/");
    }

    /**
     * Reads from inbox, uppercases the content, writes to processed.
     * Inbox is intentionally left as-is — no delete permission there.
     */
    public String processFile(String objectKey) {
        String sourcePath = inboxPath     + "/" + objectKey;
        String destPath   = processedPath + "/" + objectKey;

        auth.assertCanRead(CallerRole.PROCESSOR, sourcePath);
        auth.assertCanWrite(CallerRole.PROCESSOR, destPath);

        byte[] raw         = filer.download(sourcePath);
        byte[] transformed = new String(raw).toUpperCase().getBytes();
        filer.upload(destPath, transformed, "text/plain");

        return "Processed %s -> %s (%d bytes)".formatted(sourcePath, destPath, transformed.length);
    }
}
