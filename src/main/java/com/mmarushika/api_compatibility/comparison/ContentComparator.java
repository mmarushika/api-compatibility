package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeContext;
import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.ComparisonContext;
import com.mmarushika.api_compatibility.model.DetectedChange;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ContentComparator {

    private final SchemaComparator schemaComparator;

    public ContentComparator(SchemaComparator schemaComparator) {
        this.schemaComparator = Objects.requireNonNull(
                schemaComparator,
                "schemaComparator must not be null");
    }

    public List<DetectedChange> compare(
            ComparisonContext context,
            ChangeLocation location,
            Content oldContent,
            Content newContent) {

        Map<String, MediaType> previous = oldContent == null ? Map.of() : oldContent;
        Map<String, MediaType> current = newContent == null ? Map.of() : newContent;

        boolean request = location.context() == ChangeContext.REQUEST;

        List<DetectedChange> changes = new ArrayList<>();

        for (Map.Entry<String, MediaType> entry : previous.entrySet()) {

            String mediaType = entry.getKey();

            if (!current.containsKey(mediaType)) {
                changes.add(change(
                        request
                                ? ChangeType.REQUEST_MEDIA_TYPE_REMOVED
                                : ChangeType.RESPONSE_MEDIA_TYPE_REMOVED,
                        CompatibilityImpact.BREAKING,
                        location.withMediaType(mediaType),
                        "Media type " + mediaType + " of " + location.describe()
                                + " was removed."));
            }
        }

        for (Map.Entry<String, MediaType> entry : current.entrySet()) {

            String mediaType = entry.getKey();

            if (!previous.containsKey(mediaType)) {
                changes.add(change(
                        request
                                ? ChangeType.REQUEST_MEDIA_TYPE_ADDED
                                : ChangeType.RESPONSE_MEDIA_TYPE_ADDED,
                        CompatibilityImpact.NON_BREAKING,
                        location.withMediaType(mediaType),
                        "Media type " + mediaType + " of " + location.describe()
                                + " was added."));
            }
        }

        for (Map.Entry<String, MediaType> entry : previous.entrySet()) {

            MediaType newMediaType = current.get(entry.getKey());

            if (newMediaType != null) {
                changes.addAll(schemaComparator.compare(
                        context,
                        location.withMediaType(entry.getKey()),
                        schemaOf(entry.getValue()),
                        schemaOf(newMediaType)));
            }
        }

        return List.copyOf(changes);
    }

    private static Schema<?> schemaOf(MediaType mediaType) {
        return mediaType == null ? null : mediaType.getSchema();
    }

    private static DetectedChange change(
            ChangeType type,
            CompatibilityImpact impact,
            ChangeLocation location,
            String message) {

        return new DetectedChange(type, impact, location, message);
    }
}
