package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.ComparisonContext;
import com.mmarushika.api_compatibility.model.DetectedChange;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class PathComparator {

    private final OperationComparator operationComparator;

    public PathComparator(OperationComparator operationComparator) {
        this.operationComparator = Objects.requireNonNull(
                operationComparator,
                "operationComparator must not be null");
    }

    public List<DetectedChange> compare(ComparisonContext context) {

        Map<String, PathItem> previous = pathsOf(context.oldOpenAPI());
        Map<String, PathItem> current = pathsOf(context.newOpenAPI());

        List<DetectedChange> changes = new ArrayList<>();

        for (Map.Entry<String, PathItem> entry : previous.entrySet()) {

            String path = entry.getKey();

            if (!current.containsKey(path)) {
                changes.add(removed(path));
                continue;
            }

            changes.addAll(operationComparator.compare(
                    context,
                    path,
                    entry.getValue(),
                    current.get(path)));
        }

        for (String path : current.keySet()) {
            if (!previous.containsKey(path)) {
                changes.add(added(path));
            }
        }

        return List.copyOf(changes);
    }

    private static Map<String, PathItem> pathsOf(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getPaths() == null) {
            return Map.of();
        }
        return openAPI.getPaths();
    }

    // change: removed path
    private static DetectedChange removed(String path) {
        return new DetectedChange(
                ChangeType.PATH_REMOVED,
                CompatibilityImpact.BREAKING,
                ChangeLocation.forPath(path),
                "Path '" + path + "' was removed.");
    }
    // change: added path
    private static DetectedChange added(String path) {
        return new DetectedChange(
                ChangeType.PATH_ADDED,
                CompatibilityImpact.NON_BREAKING,
                ChangeLocation.forPath(path),
                "Path '" + path + "' was added.");
    }
}