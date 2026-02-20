package com.gt.ssrs.blob.model;

import java.time.Instant;

public record BlobPath(String path, boolean isRelative, Instant expiration) { }
