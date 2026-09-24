package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.DetectedChange;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;

import java.util.List;
import java.util.Map;
import java.util.Objects;

// entry point for comparison of input OpenAPI objects
public class ComparisonEngine {

    private final PathComparator pathComparator;

    public ComparisonEngine(PathComparator pathComparator) {
        this.pathComparator = Objects.requireNonNull(
                pathComparator,
                "pathComparator must not be null");
    }
    
    public List<DetectedChange> compare(OpenAPI oldSpec, OpenAPI newSpec) {
        return pathComparator.compare(pathsOf(oldSpec), pathsOf(newSpec));
    }

    private static Map<String, PathItem> pathsOf(OpenAPI spec) {
        if (spec == null || spec.getPaths() == null) {
            return Map.of(); // creates empty map
        }
        return spec.getPaths();
    }
}
