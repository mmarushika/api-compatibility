package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.DetectedChange;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class OperationComparator {

    private final ParameterComparator parameterComparator;

    public OperationComparator(ParameterComparator parameterComparator) {
        this.parameterComparator = Objects.requireNonNull(
                parameterComparator,
                "parameterComparator must not be null");
    }

    public List<DetectedChange> compare(
            String path,
            PathItem oldPathItem,
            PathItem newPathItem) {

        Map<HttpMethod, Operation> oldOperations = operationsOf(oldPathItem);
        Map<HttpMethod, Operation> newOperations = operationsOf(newPathItem);

        List<DetectedChange> changes = new ArrayList<>();

        for (Map.Entry<HttpMethod, Operation> entry : oldOperations.entrySet()) {

            HttpMethod method = entry.getKey();
            Operation newOperation = newOperations.get(method);

            if (newOperation == null) {
                changes.add(removed(path, method));
                continue;
            }

            changes.addAll(parameterComparator.compare(
                    path,
                    method.name(),
                    ParameterComparator.resolveEffectiveParameters(oldPathItem, entry.getValue()),
                    ParameterComparator.resolveEffectiveParameters(newPathItem, newOperation)));
        }

        for (HttpMethod method : newOperations.keySet()) {
            if (!oldOperations.containsKey(method)) {
                changes.add(added(path, method));
            }
        }
        // TODO : call requestBodyComparator.compare(path, method.name(), oldOperation, newOperation);
        // TODO : call responseComparator.compare(path, method.name(), oldOperation, newOperation);

        return List.copyOf(changes);
    }

    private static Map<HttpMethod, Operation> operationsOf(PathItem pathItem) {
        return pathItem == null ? Map.of() : pathItem.readOperationsMap();
    }

    private static DetectedChange removed(String path, HttpMethod method) {
        return new DetectedChange(
                ChangeType.OPERATION_REMOVED,
                CompatibilityImpact.BREAKING,
                ChangeLocation.forOperation(path, method.name()),
                "Operation " + method.name() + " " + path + " was removed.");
    }

    private static DetectedChange added(String path, HttpMethod method) {
        return new DetectedChange(
                ChangeType.OPERATION_ADDED,
                CompatibilityImpact.NON_BREAKING,
                ChangeLocation.forOperation(path, method.name()),
                "Operation " + method.name() + " " + path + " was added.");
    }
}