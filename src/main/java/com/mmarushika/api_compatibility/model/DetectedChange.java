package com.mmarushika.api_compatibility.model;

import java.util.Objects;

public record DetectedChange(
        ChangeType type,
        CompatibilityImpact impact,
        ChangeLocation location,
        String message) {

    public DetectedChange {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(impact, "impact must not be null");
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    @Override
    public String toString() {
        return type + " [" + impact + "] " + location.describe() + " - " + message;
    }
}
