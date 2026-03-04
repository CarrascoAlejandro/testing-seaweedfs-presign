package com.example.seaweedfs.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Intentionally attempts operations that violate the configured ACL policy
 * to demonstrate that SeaweedFS enforces access at the infrastructure level.
 */
@Service
public class ProbeService {

    private final S3Client readerClient;
    private final S3Client processorClient;

    public ProbeService(@Qualifier("readerClient") S3Client readerClient,
                        @Qualifier("processorClient") S3Client processorClient) {
        this.readerClient = readerClient;
        this.processorClient = processorClient;
    }

    /**
     * reader-service (Read:processed, List:processed only) attempts to GET from inbox.
     * Expected: SeaweedFS returns 403 Forbidden.
     *
     * @return the HTTP status code returned by SeaweedFS
     */
    public int readerReadsInbox(String key) {
        try {
            readerClient.getObjectAsBytes(r -> r.bucket("inbox").key(key));
            return 200;
        } catch (S3Exception e) {
            return e.statusCode();
        }
    }

    /**
     * processor-service (Read:inbox, List:inbox, Write:processed) attempts to PUT to inbox.
     * Expected: SeaweedFS returns 403 Forbidden.
     *
     * @return the HTTP status code returned by SeaweedFS
     */
    public int processorWritesInbox(String key) {
        try {
            processorClient.putObject(
                    r -> r.bucket("inbox").key(key),
                    RequestBody.fromString("unauthorized write attempt"));
            return 200;
        } catch (S3Exception e) {
            return e.statusCode();
        }
    }
}
