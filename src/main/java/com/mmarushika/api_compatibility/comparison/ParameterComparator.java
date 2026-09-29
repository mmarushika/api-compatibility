package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.ComparisonContext;
import com.mmarushika.api_compatibility.model.DetectedChange;
import com.mmarushika.api_compatibility.resolver.ReferenceResolver;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ParameterComparator {

    private static final String PATH_LOCATION = "path";

    public List<DetectedChange> compare(
            String path,
            String method,
            List<Parameter> oldParameters,
            List<Parameter> newParameters) {

        Map<ParameterKey, Parameter> previous = indexByIdentity(oldParameters);

        Map<ParameterKey, Parameter> current = indexByIdentity(newParameters);

        List<DetectedChange> changes = new ArrayList<>();

        for (Map.Entry<ParameterKey, Parameter> entry : previous.entrySet()) {

            ParameterKey key = entry.getKey();
            Parameter newParameter = current.get(key);

            if (newParameter == null) {
                changes.add(removed(path, method, key, entry.getValue()));
                continue;
            }

            changes.addAll(
                    requirednessChange(
                            path,
                            method,
                            key,
                            entry.getValue(),
                            newParameter
                    )
            );
        }

        for (Map.Entry<ParameterKey, Parameter> entry : current.entrySet()) {
            if (!previous.containsKey(entry.getKey())) {
                changes.add(added(path, method, entry.getKey(), entry.getValue()));
            }
        }

        return List.copyOf(changes);
    }

    public static List<Parameter> resolveOldEffectiveParameters(
            ComparisonContext context,
            PathItem pathItem,
            Operation operation) {

        return resolveEffectiveParameters(
                context.oldOpenAPI(), context.referenceResolver(), pathItem, operation);
    }

    public static List<Parameter> resolveNewEffectiveParameters(
            ComparisonContext context,
            PathItem pathItem,
            Operation operation) {

        return resolveEffectiveParameters(
                context.newOpenAPI(), context.referenceResolver(), pathItem, operation);
    }

    private static List<Parameter> resolveEffectiveParameters(
            OpenAPI openAPI,
            ReferenceResolver referenceResolver,
            PathItem pathItem,
            Operation operation) {

        Map<ParameterKey, Parameter> effective = new LinkedHashMap<>();

        mergeInto(
                effective,
                pathItem == null ? null : pathItem.getParameters(),
                openAPI,
                referenceResolver);

        mergeInto(
                effective,
                operation == null ? null : operation.getParameters(),
                openAPI,
                referenceResolver);

        return List.copyOf(effective.values());
    }

    private static void mergeInto(
            Map<ParameterKey, Parameter> effective,
            List<Parameter> parameters,
            OpenAPI openAPI,
            ReferenceResolver referenceResolver) {

        if (parameters == null) {
            return;
        }

        for (Parameter parameter : parameters) {

            Parameter resolved = resolve(parameter, openAPI, referenceResolver);

            if (resolved == null || resolved.getName() == null) {
                continue;
            }

            effective.put(
                    new ParameterKey(resolved.getName(), resolved.getIn()),
                    resolved
            );
        }
    }

    private List<DetectedChange> requirednessChange(
            String path,
            String method,
            ParameterKey key,
            Parameter oldParameter,
            Parameter newParameter) {

        boolean wasRequired = isRequired(oldParameter);
        boolean isRequired = isRequired(newParameter);

        if (wasRequired == isRequired) {
            return List.of();
        }

        if (isRequired) {
            return List.of(new DetectedChange(
                    ChangeType.PARAMETER_REQUIREDNESS_ADDED,
                    CompatibilityImpact.BREAKING,
                    location(path, method, key),
                    "Parameter " + key.describe() + " of " + method + " " + path
                            + " was optional and is now required."
            ));
        }

        return List.of(new DetectedChange(
                ChangeType.PARAMETER_REQUIREDNESS_REMOVED,
                CompatibilityImpact.NON_BREAKING,
                location(path, method, key),
                "Parameter " + key.describe() + " of " + method + " " + path
                        + " was required and is now optional."
        ));
    }

    private DetectedChange removed(
            String path,
            String method,
            ParameterKey key,
            Parameter oldParameter) {

        return new DetectedChange(
                ChangeType.PARAMETER_REMOVED,
                impactOf(isRequired(oldParameter)),
                location(path, method, key),
                "Parameter " + key.describe() + " of " + method + " " + path + " was removed."
        );
    }

    private DetectedChange added(
            String path,
            String method,
            ParameterKey key,
            Parameter newParameter) {

        return new DetectedChange(
                ChangeType.PARAMETER_ADDED,
                impactOf(isRequired(newParameter)),
                location(path, method, key),
                "Parameter " + key.describe() + " of " + method + " " + path + " was added."
        );
    }

    private CompatibilityImpact impactOf(boolean required) {
        return required
                ? CompatibilityImpact.BREAKING
                : CompatibilityImpact.NON_BREAKING;
    }

    private boolean isRequired(Parameter parameter) {
        if (parameter.getRequired() != null) {
            return parameter.getRequired();
        }

        return PATH_LOCATION.equals(parameter.getIn());
    }

    private ChangeLocation location(
            String path,
            String method,
            ParameterKey key) {

        return ChangeLocation.forParameter(
                path,
                method,
                key.name(),
                key.in()
        );
    }

    private static Map<ParameterKey, Parameter> indexByIdentity(List<Parameter> parameters) {

        Map<ParameterKey, Parameter> identified = new LinkedHashMap<>();

        if (parameters == null) {
            return identified;
        }

        for (Parameter parameter : parameters) {

            if (parameter == null || parameter.getName() == null) {
                continue;
            }

            identified.put(
                    new ParameterKey(parameter.getName(), parameter.getIn()),
                    parameter
            );
        }

        return identified;
    }

    private static Parameter resolve(
            Parameter parameter,
            OpenAPI openAPI,
            ReferenceResolver referenceResolver) {

        if (parameter == null || parameter.get$ref() == null) {
            return parameter;
        }

        Object resolved =
                referenceResolver.resolve(parameter.get$ref(), openAPI);

        if (resolved instanceof Parameter resolvedParameter) {
            return resolvedParameter;
        }

        return null;
    }

    private record ParameterKey(String name, String in) {

        String describe() {
            return in == null
                    ? name
                    : in + "." + name;
        }
    }
}