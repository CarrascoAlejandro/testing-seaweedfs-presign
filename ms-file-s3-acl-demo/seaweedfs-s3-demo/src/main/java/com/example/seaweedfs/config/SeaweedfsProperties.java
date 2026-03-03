package com.example.seaweedfs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seaweedfs")
public record SeaweedfsProperties(
        String endpoint,
        String publicEndpoint
) {}
