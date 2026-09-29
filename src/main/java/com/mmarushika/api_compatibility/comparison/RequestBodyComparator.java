package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.ComparisonContext;
import com.mmarushika.api_compatibility.model.DetectedChange;
import com.mmarushika.api_compatibility.resolver.ReferenceResolver;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.parameters.RequestBody;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class RequestBodyComparator {

    private final ContentComparator contentComparator;

    public RequestBodyComparator(ContentComparator contentComparator) {
        this.contentComparator = Objects.requireNonNull(
                contentComparator,
                "contentComparator must not be null");
    }

    public List<DetectedChange> compare(
            ComparisonContext context,
            String path,
            String method,
            RequestBody oldRequestBody,
            RequestBody newRequestBody) {

        RequestBody old = resolve(
                context.oldOpenAPI(), context.referenceResolver(), oldRequestBody);
        RequestBody current = resolve(
                context.newOpenAPI(), context.referenceResolver(), newRequestBody);

        if (unresolved(oldRequestBody, old) || unresolved(newRequestBody, current)) {
            return List.of();
        }

        ChangeLocation location = ChangeLocation.forRequestBody(path, method);

        if (old == null && current == null) {
            return List.of();
        }

        if (old == null) {
            return List.of(change(
                    ChangeType.REQUEST_BODY_ADDED,
                    impactOf(isRequired(current)),
                    location,
                    "Request body of " + method + " " + path + " was added."));
        }

        if (current == null) {
            return List.of(change(
                    ChangeType.REQUEST_BODY_REMOVED,
                    CompatibilityImpact.BREAKING,
                    location,
                    "Request body of " + method + " " + path + " was removed."));
        }

        List<DetectedChange> changes = new ArrayList<>();

        boolean wasRequired = isRequired(old);
        boolean isRequired = isRequired(current);

        if (wasRequired != isRequired) {
            changes.add(change(
                    isRequired
                            ? ChangeType.REQUEST_BODY_REQUIREDNESS_ADDED
                            : ChangeType.REQUEST_BODY_REQUIREDNESS_REMOVED,
                    isRequired
                            ? CompatibilityImpact.BREAKING
                            : CompatibilityImpact.NON_BREAKING,
                    location,
                    isRequired
                            ? "Request body of " + method + " " + path
                                    + " was optional and is now required."
                            : "Request body of " + method + " " + path
                                    + " was required and is now optional."));
        }

        changes.addAll(contentComparator.compare(
                context, location, old.getContent(), current.getContent()));

        return List.copyOf(changes);
    }

    private static RequestBody resolve(
            OpenAPI openAPI,
            ReferenceResolver referenceResolver,
            RequestBody requestBody) {

        if (requestBody == null || requestBody.get$ref() == null) {
            return requestBody;
        }

        Object resolved = referenceResolver.resolve(requestBody.get$ref(), openAPI);

        return resolved instanceof RequestBody resolvedBody ? resolvedBody : null;
    }

    private static boolean unresolved(RequestBody requestBody, RequestBody resolved) {
        return requestBody != null && requestBody.get$ref() != null && resolved == null;
    }

    private static boolean isRequired(RequestBody requestBody) {
        return Boolean.TRUE.equals(requestBody.getRequired());
    }

    private static CompatibilityImpact impactOf(boolean required) {
        return required
                ? CompatibilityImpact.BREAKING
                : CompatibilityImpact.NON_BREAKING;
    }

    private static DetectedChange change(
            ChangeType type,
            CompatibilityImpact impact,
            ChangeLocation location,
            String message) {

        return new DetectedChange(type, impact, location, message);
    }
}
