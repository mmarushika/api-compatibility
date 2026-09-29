package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ComparisonContext;
import com.mmarushika.api_compatibility.model.DetectedChange;
import com.mmarushika.api_compatibility.resolver.ReferenceResolver;

import io.swagger.v3.oas.models.OpenAPI;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

// entry point for comparison of input OpenAPI objects
@Component
public class ComparisonEngine {

    private final PathComparator pathComparator;

    public ComparisonEngine(PathComparator pathComparator) {
        this.pathComparator = Objects.requireNonNull(
                pathComparator,
                "pathComparator must not be null");
    }

    public List<DetectedChange> compare(OpenAPI oldSpec, OpenAPI newSpec) {
        ComparisonContext context =
                new ComparisonContext(oldSpec, newSpec, new ReferenceResolver());

        return pathComparator.compare(context);
    }
}
