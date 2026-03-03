package com.example.seaweedfs.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

/**
 * Uses the processor-service identity:
 *   - can read from inbox
 *   - can write/delete in processed
 *   - cannot write to inbox or read from processed
 */
@Service
public class ProcessorService {

    private final S3Client client;

    public ProcessorService(@Qualifier("processorClient") S3Client client) {
        this.client = client;
    }

    public List<String> listInbox() {
        return client.listObjectsV2(r -> r.bucket("inbox"))
                .contents()
                .stream()
                .map(S3Object::key)
                .toList();
    }

    /**
     * Reads an object from inbox and writes it to processed.
     * Inbox is intentionally left as-is (processor has no delete permission there).
     */
    public String process(String key) {
        byte[] data = client.getObjectAsBytes(r -> r.bucket("inbox").key(key)).asByteArray();

        client.putObject(
                r -> r.bucket("processed").key(key),
                RequestBody.fromBytes(data));

        return "Processed '" + key + "' — " + data.length + " bytes written to processed/";
    }
}
