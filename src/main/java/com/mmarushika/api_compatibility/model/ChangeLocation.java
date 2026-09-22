package com.mmarushika.api_compatibility.model;

public record ChangeLocation(
        String path,
        String method,
        String field
) {
}