package com.example.seaweedfs.service;

import com.example.seaweedfs.config.SeaweedfsProperties;
import com.example.seaweedfs.exception.AccessDeniedException;
import com.example.seaweedfs.model.CallerRole;
import org.springframework.stereotype.Service;

/**
 * Enforces per-bucket, per-operation access rules in the application layer.
 * SeaweedFS Filer JWT tokens are not path-scoped — a valid write token grants
 * write access to the entire Filer. This class is the only safeguard.
 *
 * Permission matrix:
 *   CLIENT    — write to inbox only, no reads
 *   PROCESSOR — read from inbox, write to processed
 *   READER    — read from processed only
 */
@Service
public class FilerAuthorizationService {

    private final String inboxPath;
    private final String processedPath;

    public FilerAuthorizationService(SeaweedfsProperties props) {
        this.inboxPath     = props.getFiler().getBuckets().getInbox();
        this.processedPath = props.getFiler().getBuckets().getProcessed();
    }

    public void assertCanWrite(CallerRole role, String targetPath) {
        boolean permitted = switch (role) {
            case CLIENT    -> targetPath.startsWith(inboxPath + "/");
            case PROCESSOR -> targetPath.startsWith(processedPath + "/");
            case READER    -> false;
        };
        if (!permitted) {
            throw new AccessDeniedException(
                "Role %s is not permitted to write to: %s".formatted(role, targetPath)
            );
        }
    }

    public void assertCanRead(CallerRole role, String targetPath) {
        boolean permitted = switch (role) {
            case CLIENT    -> false;
            case PROCESSOR -> targetPath.startsWith(inboxPath + "/");
            case READER    -> targetPath.startsWith(processedPath + "/");
        };
        if (!permitted) {
            throw new AccessDeniedException(
                "Role %s is not permitted to read: %s".formatted(role, targetPath)
            );
        }
    }
}
