package com.example.seaweedfs.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

/**
 * Uses the reader-service identity, which has read-only access to processed.
 * Any attempt to read from inbox or write anywhere will be rejected by SeaweedFS.
 */
@Service
public class ReaderService {

    private final S3Client client;

    public ReaderService(@Qualifier("readerClient") S3Client client) {
        this.client = client;
    }

    public List<String> listProcessed() {
        return client.listObjectsV2(r -> r.bucket("processed"))
                .contents()
                .stream()
                .map(S3Object::key)
                .toList();
    }

    public String read(String key) {
        return client.getObjectAsBytes(r -> r.bucket("processed").key(key)).asUtf8String();
    }
}
