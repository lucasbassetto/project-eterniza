package com.eterniza.photo.dto;

import java.util.UUID;

public record PhotoUploadResponse(UUID photoId, String message) {}
