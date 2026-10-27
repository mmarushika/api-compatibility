package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.ComparisonContext;
import com.mmarushika.api_compatibility.model.DetectedChange;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


@Component
public class OperationComparator {

    private final ParameterComparator parameterComparator;
    private final RequestBodyComparator requestBodyComparator;
    private final ResponseComparator responseComparator;

    public OperationComparator(
            ParameterComparator parameterComparator,
            RequestBodyComparator requestBodyComparator,
            ResponseComparator responseComparator) {

        this.parameterComparator = Objects.requireNonNull(
                parameterComparator,
                "parameterComparator must not be null");
        this.requestBodyComparator = Objects.requireNonNull(
                requestBodyComparator,
                "requestBodyComparator must not be null");
        this.responseComparator = Objects.requireNonNull(
                responseComparator,
                "responseComparator must not be null");
    }

    public List<DetectedChange> compare(
            ComparisonContext context,
            String path,
            PathItem oldPathItem,
            PathItem newPathItem) {

        Map<HttpMethod, Operation> oldOperations = operationsOf(oldPathItem);
        Map<HttpMethod, Operation> newOperations = operationsOf(newPathItem);

        List<DetectedChange> changes = new ArrayList<>();

        for (Map.Entry<HttpMethod, Operation> entry : oldOperations.entrySet()) {

            HttpMethod method = entry.getKey();
            Operation oldOperation = entry.getValue();
            Operation newOperation = newOperations.get(method);

            if (newOperation == null) {
                changes.add(removed(path, method));
                continue;
            }

            changes.addAll(parameterComparator.compare(
                    context,
                    path,
                    method.name(),
                    ParameterComparator.resolveOldEffectiveParameters(
                            context, oldPathItem, oldOperation),
                    ParameterComparator.resolveNewEffectiveParameters(
                            context, newPathItem, newOperation)));

            changes.addAll(requestBodyComparator.compare(
                    context,
                    path,
                    method.name(),
                    oldOperation.getRequestBody(),
                    newOperation.getRequestBody()));

            changes.addAll(responseComparator.compare(
                    context,
                    path,
                    method.name(),
                    oldOperation.getResponses(),
                    newOperation.getResponses()));

            changes.addAll(compareSecurity(
                    context,
                    path,
                    method.name(),
                    oldOperation,
                    newOperation));
        }

        for (HttpMethod method : newOperations.keySet()) {
            if (!oldOperations.containsKey(method)) {
                changes.add(added(path, method));
            }
        }

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

    private List<DetectedChange> compareSecurity(
            ComparisonContext context,
            String path,
            String method,
            Operation oldOperation,
            Operation newOperation) {

        List<io.swagger.v3.oas.models.security.SecurityRequirement> oldSec =
                resolveEffectiveSecurity(context.oldOpenAPI(), oldOperation);
        List<io.swagger.v3.oas.models.security.SecurityRequirement> newSec =
                resolveEffectiveSecurity(context.newOpenAPI(), newOperation);

        if (oldSec.isEmpty() && newSec.isEmpty()) {
            return List.of();
        }

        List<DetectedChange> changes = new ArrayList<>();
        ChangeLocation location = ChangeLocation.forOperation(path, method);

        if (oldSec.isEmpty() && !newSec.isEmpty()) {
            changes.add(new DetectedChange(
                    ChangeType.OPERATION_REQUIREDNESS_ADDED,
                    CompatibilityImpact.BREAKING,
                    location,
                    "Operation " + method + " " + path + " was unauthenticated and now requires security."));
            return changes;
        }

        if (!oldSec.isEmpty() && newSec.isEmpty()) {
            changes.add(new DetectedChange(
                    ChangeType.OPERATION_REQUIREDNESS_REMOVED,
                    CompatibilityImpact.NON_BREAKING,
                    location,
                    "Operation " + method + " " + path + " required security and is now public."));
            return changes;
        }

        Set<String> oldSchemes = extractSchemeNames(oldSec);
        Set<String> newSchemes = extractSchemeNames(newSec);

        for (String scheme : oldSchemes) {
            if (!newSchemes.contains(scheme)) {
                changes.add(new DetectedChange(
                        ChangeType.OPERATION_REQUIREDNESS_ADDED,
                        CompatibilityImpact.BREAKING,
                        location,
                        "Security scheme " + scheme + " was removed from operation " + method + " " + path + "."));
            }
        }

        for (String scheme : newSchemes) {
            if (!oldSchemes.contains(scheme)) {
                changes.add(new DetectedChange(
                        ChangeType.OPERATION_REQUIREDNESS_REMOVED,
                        CompatibilityImpact.NON_BREAKING,
                        location,
                        "Security scheme alternative " + scheme + " was added to operation " + method + " " + path + "."));
            }
        }

        for (String scheme : oldSchemes) {
            if (newSchemes.contains(scheme)) {
                Set<String> oldScopes = extractScopes(oldSec, scheme);
                Set<String> newScopes = extractScopes(newSec, scheme);
                Set<String> addedScopes = new LinkedHashSet<>(newScopes);
                addedScopes.removeAll(oldScopes);
                if (!addedScopes.isEmpty()) {
                    changes.add(new DetectedChange(
                            ChangeType.OPERATION_REQUIREDNESS_ADDED,
                            CompatibilityImpact.BREAKING,
                            location,
                            "Security scheme " + scheme + " gained required scopes " + addedScopes
                                    + " on operation " + method + " " + path + "."));
                }
            }
        }

        return changes;
    }

    private static List<io.swagger.v3.oas.models.security.SecurityRequirement> resolveEffectiveSecurity(
            OpenAPI openAPI, Operation operation) {
        if (operation != null && operation.getSecurity() != null) {
            return operation.getSecurity();
        }
        if (openAPI != null && openAPI.getSecurity() != null) {
            return openAPI.getSecurity();
        }
        return List.of();
    }

    private static Set<String> extractSchemeNames(
            List<io.swagger.v3.oas.models.security.SecurityRequirement> requirements) {
        Set<String> names = new LinkedHashSet<>();
        for (var req : requirements) {
            if (req != null) {
                names.addAll(req.keySet());
            }
        }
        return names;
    }

    private static Set<String> extractScopes(
            List<io.swagger.v3.oas.models.security.SecurityRequirement> requirements, String scheme) {
        Set<String> scopes = new LinkedHashSet<>();
        for (var req : requirements) {
            if (req != null && req.containsKey(scheme) && req.get(scheme) != null) {
                scopes.addAll(req.get(scheme));
            }
        }
        return scopes;
    }
}
