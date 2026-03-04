package com.example.seaweedfs.service;

import com.example.seaweedfs.config.SeaweedfsProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Generates HS256 JWTs that match the format SeaweedFS's internal
 * GenJwtForFilerServer produces. The token contains only an expiry claim.
 * SeaweedFS does not inspect any claim other than exp.
 */
@Service
public class FilerJwtTokenService {

    private final String writeSecret;
    private final String readSecret;
    private final int    ttlSeconds;

    public FilerJwtTokenService(SeaweedfsProperties props) {
        this.writeSecret = props.getJwt().getWriteSecret();
        this.readSecret  = props.getJwt().getReadSecret();
        this.ttlSeconds  = props.getJwt().getTtlSeconds();
    }

    public String generateWriteToken() {
        return buildToken(writeSecret);
    }

    public String generateReadToken() {
        return buildToken(readSecret);
    }

    private String buildToken(String secret) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(
                    Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)),
                    Jwts.SIG.HS256
                )
                .compact();
    }
}
