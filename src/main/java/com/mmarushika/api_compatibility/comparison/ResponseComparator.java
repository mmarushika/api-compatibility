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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ResponseComparator {

    private final ContentComparator contentComparator;
    private final SchemaComparator schemaComparator;

    @Autowired
    public ResponseComparator(ContentComparator contentComparator, SchemaComparator schemaComparator) {
        this.contentComparator = Objects.requireNonNull(
                contentComparator,
                "contentComparator must not be null");
        this.schemaComparator = Objects.requireNonNull(
                schemaComparator,
                "schemaComparator must not be null");
    }

    public ResponseComparator(ContentComparator contentComparator) {
        this(contentComparator, new SchemaComparator());
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

            changes.addAll(compareHeaders(
                    context,
                    path,
                    method,
                    statusCode,
                    oldResponse.getHeaders(),
                    resolvedNew.getHeaders()));
        }

        return List.copyOf(changes);
    }

    private List<DetectedChange> compareHeaders(
            ComparisonContext context,
            String path,
            String method,
            String statusCode,
            Map<String, io.swagger.v3.oas.models.headers.Header> oldHeadersMap,
            Map<String, io.swagger.v3.oas.models.headers.Header> newHeadersMap) {

        Map<String, io.swagger.v3.oas.models.headers.Header> previous =
                oldHeadersMap == null ? Map.of() : oldHeadersMap;
        Map<String, io.swagger.v3.oas.models.headers.Header> current =
                newHeadersMap == null ? Map.of() : newHeadersMap;

        if (previous.isEmpty() && current.isEmpty()) {
            return List.of();
        }

        List<DetectedChange> changes = new ArrayList<>();

        for (Map.Entry<String, io.swagger.v3.oas.models.headers.Header> entry : previous.entrySet()) {
            String headerName = entry.getKey();
            if (!current.containsKey(headerName)) {
                ChangeLocation location = ChangeLocation.forResponseHeader(path, method, statusCode, headerName);
                changes.add(change(
                        ChangeType.PARAMETER_REMOVED,
                        CompatibilityImpact.BREAKING,
                        location,
                        "Response header " + headerName + " of " + statusCode + " " + method + " " + path
                                + " was removed."));
            }
        }

        for (Map.Entry<String, io.swagger.v3.oas.models.headers.Header> entry : current.entrySet()) {
            String headerName = entry.getKey();
            if (!previous.containsKey(headerName)) {
                ChangeLocation location = ChangeLocation.forResponseHeader(path, method, statusCode, headerName);
                changes.add(change(
                        ChangeType.PARAMETER_ADDED,
                        CompatibilityImpact.NON_BREAKING,
                        location,
                        "Response header " + headerName + " of " + statusCode + " " + method + " " + path
                                + " was added."));
            }
        }

        for (Map.Entry<String, io.swagger.v3.oas.models.headers.Header> entry : previous.entrySet()) {
            String headerName = entry.getKey();
            io.swagger.v3.oas.models.headers.Header newHeader = current.get(headerName);
            if (newHeader == null) {
                continue;
            }

            io.swagger.v3.oas.models.headers.Header oldHeader = resolveHeader(
                    context.oldOpenAPI(), context.referenceResolver(), entry.getValue());
            io.swagger.v3.oas.models.headers.Header resolvedNew = resolveHeader(
                    context.newOpenAPI(), context.referenceResolver(), newHeader);

            if (oldHeader == null || resolvedNew == null) {
                continue;
            }

            ChangeLocation location = ChangeLocation.forResponseHeader(path, method, statusCode, headerName);

            boolean wasRequired = Boolean.TRUE.equals(oldHeader.getRequired());
            boolean isRequired = Boolean.TRUE.equals(resolvedNew.getRequired());

            if (wasRequired != isRequired) {
                changes.add(change(
                        isRequired
                                ? ChangeType.PARAMETER_REQUIREDNESS_ADDED
                                : ChangeType.PARAMETER_REQUIREDNESS_REMOVED,
                        isRequired
                                ? CompatibilityImpact.NON_BREAKING
                                : CompatibilityImpact.BREAKING,
                        location,
                        isRequired
                                ? "Response header " + headerName + " of " + statusCode + " " + method + " " + path
                                        + " was optional and is now required."
                                : "Response header " + headerName + " of " + statusCode + " " + method + " " + path
                                        + " was required and is now optional."));
            }

            io.swagger.v3.oas.models.media.Schema<?> oldSchema = extractHeaderSchema(oldHeader);
            io.swagger.v3.oas.models.media.Schema<?> currentSchema = extractHeaderSchema(resolvedNew);

            if (oldSchema != null || currentSchema != null) {
                changes.addAll(schemaComparator.compare(context, location, oldSchema, currentSchema));
            }
        }

        return changes;
    }

    private static io.swagger.v3.oas.models.headers.Header resolveHeader(
            OpenAPI openAPI,
            ReferenceResolver referenceResolver,
            io.swagger.v3.oas.models.headers.Header header) {

        if (header == null || header.get$ref() == null) {
            return header;
        }

        Object resolved = referenceResolver.resolve(header.get$ref(), openAPI);
        return resolved instanceof io.swagger.v3.oas.models.headers.Header resolvedHeader ? resolvedHeader : null;
    }

    private static io.swagger.v3.oas.models.media.Schema<?> extractHeaderSchema(
            io.swagger.v3.oas.models.headers.Header header) {
        if (header == null) {
            return null;
        }
        if (header.getSchema() != null) {
            return header.getSchema();
        }
        if (header.getContent() != null && !header.getContent().isEmpty()) {
            return header.getContent().values().iterator().next().getSchema();
        }
        return null;
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
