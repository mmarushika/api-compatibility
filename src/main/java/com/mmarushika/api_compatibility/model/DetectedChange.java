package com.mmarushika.api_compatibility.model;

public class DetectedChange {

    private final ChangeType type;
    private final ChangeLocation location;
    private final String message;

    public DetectedChange(
            ChangeType type,
            ChangeLocation location,
            String message) {

        this.type = type;
        this.location = location;
        this.message = message;
    }

    public ChangeType getType() {
        return type;
    }

    public ChangeLocation getLocation() {
        return location;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "DetectedChange{" +
                "type=" + type +
                ", location=" + location +
                ", message='" + message + '\'' +
                '}';
    }
}