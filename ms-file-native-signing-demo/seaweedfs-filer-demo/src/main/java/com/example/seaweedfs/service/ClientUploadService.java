package com.example.seaweedfs.service;

import com.example.seaweedfs.client.FilerHttpClient;
import com.example.seaweedfs.config.SeaweedfsProperties;
import com.example.seaweedfs.model.CallerRole;
import org.springframework.stereotype.Service;

/**
 * The Filer JWT approach has no native equivalent of presigned URLs.
 * All client uploads must pass through the application, which acts as a proxy
 * and generates a fresh write token for each forwarded request.
 */
@Service
public class ClientUploadService {

    private final FilerAuthorizationService auth;
    private final FilerHttpClient           filer;
    private final String                    inboxPath;

    public ClientUploadService(
            FilerAuthorizationService auth,
            FilerHttpClient filer,
            SeaweedfsProperties props) {
        this.auth      = auth;
        this.filer     = filer;
        this.inboxPath = props.getFiler().getBuckets().getInbox();
    }

    public void upload(String objectKey, byte[] data, String contentType) {
        String path = inboxPath + "/" + objectKey;
        auth.assertCanWrite(CallerRole.CLIENT, path);
        filer.upload(path, data, contentType);
    }
}
