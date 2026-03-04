package com.example.seaweedfs.model;

/**
 * Returned by POST /upload/token.
 * The client uses uploadUrl and token to PUT/POST the file directly to SeaweedFS,
 * bypassing the application entirely for the data path.
 */
public record UploadTokenResponse(String uploadUrl, String token) {}
