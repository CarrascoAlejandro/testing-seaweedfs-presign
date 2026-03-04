package com.example.seaweedfs.service;

import com.example.seaweedfs.config.SeaweedfsProperties;
import com.example.seaweedfs.model.CallerRole;
import com.example.seaweedfs.model.UploadTokenResponse;
import org.springframework.stereotype.Service;

/**
 * Issues single-use upload tokens for direct client-to-filer uploads.
 * The application enforces authorization and generates the credential,
 * but file bytes never pass through this service.
 *
 * "Single-use" semantics are enforced by the short token TTL: a token is
 * generated per upload request, scoped to a specific path, and expires in
 * seconds. SeaweedFS Filer JWTs carry no path scope — the path binding is
 * a convention enforced here at issuance time.
 */
@Service
public class ClientUploadService {

    private final FilerAuthorizationService auth;
    private final FilerJwtTokenService      tokenService;
    private final String                    inboxPath;
    private final String                    publicEndpoint;

    public ClientUploadService(
            FilerAuthorizationService auth,
            FilerJwtTokenService tokenService,
            SeaweedfsProperties props) {
        this.auth           = auth;
        this.tokenService   = tokenService;
        this.inboxPath      = props.getFiler().getBuckets().getInbox();
        this.publicEndpoint = props.getFiler().getPublicEndpoint();
    }

    /**
     * Authorizes the upload request and returns a short-lived write token
     * together with the target filer URL the client should POST to directly.
     */
    public UploadTokenResponse issueUploadToken(String objectKey) {
        String path = inboxPath + "/" + objectKey;
        auth.assertCanWrite(CallerRole.CLIENT, path);
        return new UploadTokenResponse(publicEndpoint + path, tokenService.generateWriteToken());
    }
}
