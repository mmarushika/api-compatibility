package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.DetectedChange;

import io.swagger.v3.oas.models.PathItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PathComparator {

    private final OperationComparator operationComparator;

    public PathComparator(OperationComparator operationComparator) {
        this.operationComparator = Objects.requireNonNull(
                operationComparator,
                "operationComparator must not be null");
    }

    public List<DetectedChange> compare(
            Map<String, PathItem> oldPaths,
            Map<String, PathItem> newPaths) {

        Map<String, PathItem> previous = oldPaths == null ? Map.of() : oldPaths;
        Map<String, PathItem> current = newPaths == null ? Map.of() : newPaths;

        List<DetectedChange> changes = new ArrayList<>();

        for (Map.Entry<String, PathItem> entry : previous.entrySet()) {

            String path = entry.getKey();

            if (!current.containsKey(path)) {
                changes.add(removed(path));
                continue;
            }

            changes.addAll(operationComparator.compare(
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