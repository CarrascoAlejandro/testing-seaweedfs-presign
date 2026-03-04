package com.example.seaweedfs.service;

import com.example.seaweedfs.client.FilerHttpClient;
import com.example.seaweedfs.config.SeaweedfsProperties;
import com.example.seaweedfs.model.CallerRole;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Uses the READER role: read-only access to processed bucket.
 * Any attempt to read from inbox or write anywhere is rejected by FilerAuthorizationService.
 */
@Service
public class ReaderService {

    private final FilerAuthorizationService auth;
    private final FilerHttpClient           filer;
    private final String                    processedPath;

    public ReaderService(
            FilerAuthorizationService auth,
            FilerHttpClient filer,
            SeaweedfsProperties props) {
        this.auth          = auth;
        this.filer         = filer;
        this.processedPath = props.getFiler().getBuckets().getProcessed();
    }

    public List<String> listProcessed() {
        auth.assertCanRead(CallerRole.READER, processedPath + "/listing");
        return filer.listDirectory(processedPath + "/");
    }

    public byte[] readFile(String objectKey) {
        String path = processedPath + "/" + objectKey;
        auth.assertCanRead(CallerRole.READER, path);
        return filer.download(path);
    }
}
