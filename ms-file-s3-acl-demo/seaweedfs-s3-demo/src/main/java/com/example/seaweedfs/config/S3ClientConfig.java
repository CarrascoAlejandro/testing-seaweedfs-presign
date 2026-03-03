package com.example.seaweedfs.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3ClientConfig {

    // ── client-uploader: PUT-only to inbox (used for presigning) ─────────────

    @Bean
    @Qualifier("clientUploaderPresigner")
    public S3Presigner clientUploaderPresigner(SeaweedfsProperties props) {
        return buildPresigner(props.publicEndpoint(), "client-access-key", "client-secret-key");
    }

    // ── processor-service: read inbox, write processed ────────────────────────

    @Bean
    @Qualifier("processorClient")
    public S3Client processorClient(SeaweedfsProperties props) {
        return buildClient(props.endpoint(), "processor-access-key", "processor-secret-key");
    }

    // ── reader-service: read-only on processed ────────────────────────────────

    @Bean
    @Qualifier("readerClient")
    public S3Client readerClient(SeaweedfsProperties props) {
        return buildClient(props.endpoint(), "reader-access-key", "reader-secret-key");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private S3Client buildClient(String endpoint, String accessKey, String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)  // SeaweedFS requires path-style
                        .build())
                .build();
    }

    private S3Presigner buildPresigner(String endpoint, String accessKey, String secretKey) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)  // SeaweedFS requires path-style
                        .build())
                .build();
    }
}
