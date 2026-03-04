package com.example.seaweedfs.client;

import com.example.seaweedfs.config.SeaweedfsProperties;
import com.example.seaweedfs.service.FilerJwtTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the mechanics of attaching a fresh JWT to every outbound HTTP request.
 * Makes no authorization decisions — that is FilerAuthorizationService's responsibility.
 * A new token is generated on every call to avoid expiry on slow connections.
 */
@Component
public class FilerHttpClient {

    private final String              filerEndpoint;
    private final FilerJwtTokenService tokenService;
    private final RestTemplate         http;
    private final ObjectMapper         mapper;

    public FilerHttpClient(SeaweedfsProperties props, FilerJwtTokenService tokenService) {
        this.filerEndpoint = props.getFiler().getEndpoint();
        this.tokenService  = tokenService;
        this.http          = new RestTemplate();
        this.mapper        = new ObjectMapper();
    }

    public void upload(String filerPath, byte[] data, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenService.generateWriteToken());
        headers.setContentType(MediaType.parseMediaType(contentType));
        http.exchange(
            filerEndpoint + filerPath,
            HttpMethod.POST,
            new HttpEntity<>(data, headers),
            Void.class
        );
    }

    public byte[] download(String filerPath) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenService.generateReadToken());
        ResponseEntity<byte[]> response = http.exchange(
            filerEndpoint + filerPath,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            byte[].class
        );
        return response.getBody();
    }

    /**
     * Lists file names directly under a filer directory path.
     * The Filer returns a JSON directory listing for GET requests on a path ending with "/".
     * Response shape: { "Entries": [ { "FullPath": "/buckets/inbox/foo.txt" }, ... ] }
     */
    public List<String> listDirectory(String filerPath) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenService.generateReadToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = http.exchange(
            filerEndpoint + filerPath,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class
        );

        List<String> keys = new ArrayList<>();
        try {
            JsonNode root    = mapper.readTree(response.getBody());
            JsonNode entries = root.path("Entries");
            if (entries.isArray()) {
                for (JsonNode entry : entries) {
                    String fullPath = entry.path("FullPath").asText();
                    // Strip the directory prefix to return the object key only
                    String key = fullPath.substring(fullPath.lastIndexOf('/') + 1);
                    if (!key.isEmpty()) {
                        keys.add(key);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse filer directory listing", e);
        }
        return keys;
    }

    public void createDirectory(String filerPath) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + tokenService.generateWriteToken());
        headers.setContentType(MediaType.parseMediaType(""));
        http.exchange(
            filerEndpoint + filerPath + "/",
            HttpMethod.POST,
            new HttpEntity<>(headers),
            Void.class
        );
    }
}
