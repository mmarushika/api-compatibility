package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.ComparisonContext;
import com.mmarushika.api_compatibility.model.DetectedChange;
import com.mmarushika.api_compatibility.resolver.ReferenceResolver;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ResponseComparator {

    private final ContentComparator contentComparator;

    public ResponseComparator(ContentComparator contentComparator) {
        this.contentComparator = Objects.requireNonNull(
                contentComparator,
                "contentComparator must not be null");
    }

    public List<DetectedChange> compare(
            ComparisonContext context,
            String path,
            String method,
            ApiResponses oldResponses,
            ApiResponses newResponses) {

        Map<String, ApiResponse> previous = oldResponses == null ? Map.of() : oldResponses;
        Map<String, ApiResponse> current = newResponses == null ? Map.of() : newResponses;

        List<DetectedChange> changes = new ArrayList<>();

        for (Map.Entry<String, ApiResponse> entry : previous.entrySet()) {

            String statusCode = entry.getKey();

            if (!current.containsKey(statusCode)) {
                changes.add(change(
                        ChangeType.RESPONSE_REMOVED,
                        CompatibilityImpact.BREAKING,
                        ChangeLocation.forResponse(path, method, statusCode),
                        "Response " + statusCode + " of " + method + " " + path
                                + " was removed."));
            }
        }

        for (Map.Entry<String, ApiResponse> entry : current.entrySet()) {

            String statusCode = entry.getKey();

            if (!previous.containsKey(statusCode)) {
                changes.add(change(
                        ChangeType.RESPONSE_ADDED,
                        CompatibilityImpact.NON_BREAKING,
                        ChangeLocation.forResponse(path, method, statusCode),
                        "Response " + statusCode + " of " + method + " " + path
                                + " was added."));
            }
        }

        for (Map.Entry<String, ApiResponse> entry : previous.entrySet()) {

            String statusCode = entry.getKey();
            ApiResponse newResponse = current.get(statusCode);

            if (newResponse == null) {
                continue;
            }

            ApiResponse oldResponse = resolve(
                    context.oldOpenAPI(), context.referenceResolver(), entry.getValue());
            ApiResponse resolvedNew = resolve(
                    context.newOpenAPI(), context.referenceResolver(), newResponse);

            if (unresolved(entry.getValue(), oldResponse)
                    || unresolved(newResponse, resolvedNew)
                    || oldResponse == null
                    || resolvedNew == null) {
                continue;
            }

            changes.addAll(contentComparator.compare(
                    context,
                    ChangeLocation.forResponse(path, method, statusCode),
                    oldResponse.getContent(),
                    resolvedNew.getContent()));
        }

        return List.copyOf(changes);
    }

    private static ApiResponse resolve(
            OpenAPI openAPI,
            ReferenceResolver referenceResolver,
            ApiResponse response) {

        if (response == null || response.get$ref() == null) {
            return response;
        }

        Object resolved = referenceResolver.resolve(response.get$ref(), openAPI);

        return resolved instanceof ApiResponse resolvedResponse ? resolvedResponse : null;
    }

    private static boolean unresolved(ApiResponse response, ApiResponse resolved) {
        return response != null && response.get$ref() != null && resolved == null;
    }

    private static DetectedChange change(
            ChangeType type,
            CompatibilityImpact impact,
            ChangeLocation location,
            String message) {

        return new DetectedChange(type, impact, location, message);
    }
}
