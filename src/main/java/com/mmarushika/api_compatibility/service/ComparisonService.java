package com.mmarushika.api_compatibility.service;

import com.mmarushika.api_compatibility.comparison.ComparisonEngine;
import com.mmarushika.api_compatibility.model.DetectedChange;
import com.mmarushika.api_compatibility.parser.OpenApiLoader;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ComparisonService {

    private final OpenApiLoader loader;
    private final ComparisonEngine engine;

    public ComparisonService(OpenApiLoader loader, ComparisonEngine engine) {
        this.loader = Objects.requireNonNull(loader, "loader must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    public List<DetectedChange> compare(String oldDocument, String newDocument) {
        return engine.compare(loader.loadContents(oldDocument), loader.loadContents(newDocument));
    }
}
