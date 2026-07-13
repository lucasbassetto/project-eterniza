package com.eterniza.photo.dto;

import java.util.List;

public record GalleryResponse(boolean revealed, int totalPhotos, List<String> photoUrls) {}
