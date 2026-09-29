package com.mmarushika.api_compatibility.model;

import com.mmarushika.api_compatibility.resolver.ReferenceResolver;

import io.swagger.v3.oas.models.OpenAPI;

import java.util.Objects;

public record ComparisonContext(
        OpenAPI oldOpenAPI,
        OpenAPI newOpenAPI,
        ReferenceResolver referenceResolver) {

    public ComparisonContext {
        Objects.requireNonNull(referenceResolver, "referenceResolver must not be null");
    }
}
