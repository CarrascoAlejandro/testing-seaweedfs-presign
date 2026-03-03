package com.example.seaweedfs.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;

/**
 * Uses the client-uploader identity, which has PUT-only access to inbox.
 * No network call is made here — the presigner computes the signed URL locally.
 */
@Service
public class ClientUploadService {

    private final S3Presigner presigner;

    public ClientUploadService(@Qualifier("clientUploaderPresigner") S3Presigner presigner) {
        this.presigner = presigner;
    }

    public String generatePresignedPutUrl(String objectKey) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(r -> r
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(put -> put
                        .bucket("inbox")
                        .key(objectKey)));

        return presigned.url().toString();
    }
}
