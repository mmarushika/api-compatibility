package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeContext;
import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.ComparisonContext;
import com.mmarushika.api_compatibility.model.DetectedChange;
import com.mmarushika.api_compatibility.resolver.ReferenceResolver;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compares the schemas reachable from a request body or a response media type.
 *
 * <p>Compatibility is direction aware: the same structural change can be breaking for what a
 * client sends (request) while being safe for what a client receives (response), and the other way
 * around. The direction is taken from the {@link ChangeLocation#context()} of the compared schema,
 * so the comparators that own a request or a response always produce the right classification.</p>
 *
 * <p>Local {@code $ref}s are resolved through the {@link ReferenceResolver}. Resolution cycles are
 * handled by the resolver, and schema comparison cycles are tracked separately through
 * {@link SchemaPair} so a self referencing model is compared once instead of recursing forever.</p>
 */
@Component
public class SchemaComparator {

    public List<DetectedChange> compare(
            ComparisonContext context,
            ChangeLocation location,
            Schema<?> oldSchema,
            Schema<?> newSchema) {

        return compare(context, location, oldSchema, newSchema, new LinkedHashSet<>());
    }

    private List<DetectedChange> compare(
            ComparisonContext context,
            ChangeLocation location,
            Schema<?> oldSchema,
            Schema<?> newSchema,
            Set<SchemaPair> visited) {

        Resolution oldResolution = resolve(
                context.oldOpenAPI(), context.referenceResolver(), oldSchema, location);
        Resolution newResolution = resolve(
                context.newOpenAPI(), context.referenceResolver(), newSchema, location);

        if (!oldResolution.comparable() || !newResolution.comparable()) {
            return unresolvedChanges(oldResolution, newResolution);
        }

        Schema<?> old = oldResolution.schema();
        Schema<?> current = newResolution.schema();

        if (old == null && current == null) {
            return List.of();
        }

        if (old == null) {
            return List.of(change(
                    ChangeType.SCHEMA_ADDED,
                    CompatibilityImpact.NON_BREAKING,
                    location,
                    "Schema at " + location.describe() + " was added."));
        }

        if (current == null) {
            return List.of(change(
                    ChangeType.SCHEMA_REMOVED,
                    CompatibilityImpact.BREAKING,
                    location,
                    "Schema at " + location.describe() + " was removed."));
        }

        if (!visited.add(new SchemaPair(old, current))) {
            return List.of();
        }

        List<DetectedChange> changes = new ArrayList<>();

        compareType(location, old, current, changes);
        compareFormat(location, old, current, changes);
        compareNullable(location, old, current, changes);
        compareEnum(location, old, current, changes);
        compareConstraints(location, old, current, changes);
        compareAdditionalProperties(context, location, old, current, visited, changes);
        compareItems(context, location, old, current, visited, changes);
        compareProperties(context, location, old, current, visited, changes);

        return List.copyOf(changes);
    }

    private void compareType(
            ChangeLocation location,
            Schema<?> old,
            Schema<?> current,
            List<DetectedChange> changes) {

        if (old.getType() == null || current.getType() == null) {
            return;
        }

        if (!old.getType().equals(current.getType())) {
            changes.add(change(
                    ChangeType.SCHEMA_TYPE_CHANGED,
                    CompatibilityImpact.BREAKING,
                    location,
                    "Schema type at " + location.describe()
                            + " changed from " + old.getType()
                            + " to " + current.getType() + "."));
        }
    }

    private void compareFormat(
            ChangeLocation location,
            Schema<?> old,
            Schema<?> current,
            List<DetectedChange> changes) {

        // A format change is only meaningful while the underlying type stays the same; a type
        // change already subsumes it.
        if (!Objects.equals(old.getType(), current.getType())) {
            return;
        }

        if (Objects.equals(old.getFormat(), current.getFormat())) {
            return;
        }

        changes.add(change(
                ChangeType.SCHEMA_FORMAT_CHANGED,
                CompatibilityImpact.BREAKING,
                location,
                "Schema format at " + location.describe()
                        + " changed from " + describe(old.getFormat())
                        + " to " + describe(current.getFormat()) + "."));
    }

    private void compareNullable(
            ChangeLocation location,
            Schema<?> old,
            Schema<?> current,
            List<DetectedChange> changes) {

        boolean wasNullable = Boolean.TRUE.equals(old.getNullable());
        boolean isNullable = Boolean.TRUE.equals(current.getNullable());

        if (wasNullable == isNullable) {
            return;
        }

        changes.add(change(
                ChangeType.SCHEMA_NULLABLE_CHANGED,
                impact(location, !isNullable),
                location,
                isNullable
                        ? "Schema at " + location.describe() + " became nullable."
                        : "Schema at " + location.describe() + " became non-nullable."));
    }

    private void compareEnum(
            ChangeLocation location,
            Schema<?> old,
            Schema<?> current,
            List<DetectedChange> changes) {

        List<?> previous = old.getEnum() == null ? List.of() : old.getEnum();
        List<?> values = current.getEnum() == null ? List.of() : current.getEnum();

        if (previous.equals(values)) {
            return;
        }

        List<?> gained = values.stream()
                .filter(value -> !previous.contains(value))
                .toList();
        List<?> lost = previous.stream()
                .filter(value -> !values.contains(value))
                .toList();

        if (!gained.isEmpty()) {
            changes.add(change(
                    ChangeType.SCHEMA_ENUM_CHANGED,
                    impact(location, false),
                    location,
                    "Schema enum at " + location.describe() + " gained values " + gained + "."));
        }

        if (!lost.isEmpty()) {
            changes.add(change(
                    ChangeType.SCHEMA_ENUM_CHANGED,
                    impact(location, true),
                    location,
                    "Schema enum at " + location.describe() + " lost values " + lost + "."));
        }
    }


    private void compareConstraints(
            ChangeLocation location,
            Schema<?> old,
            Schema<?> current,
            List<DetectedChange> changes) {

        compareNumberConstraint(
                location, "minLength", old.getMinLength(), current.getMinLength(), true, changes);
        compareNumberConstraint(
                location, "maxLength", old.getMaxLength(), current.getMaxLength(), false, changes);
        compareNumberConstraint(
                location, "minimum", old.getMinimum(), current.getMinimum(), true, changes);
        compareNumberConstraint(
                location, "maximum", old.getMaximum(), current.getMaximum(), false, changes);
        compareNumberConstraint(
                location, "minItems", old.getMinItems(), current.getMinItems(), true, changes);
        compareNumberConstraint(
                location, "maxItems", old.getMaxItems(), current.getMaxItems(), false, changes);
        compareNumberConstraint(
                location, "minProperties", old.getMinProperties(), current.getMinProperties(),
                true, changes);
        compareNumberConstraint(
                location, "maxProperties", old.getMaxProperties(), current.getMaxProperties(),
                false, changes);

        boolean wasUnique = Boolean.TRUE.equals(old.getUniqueItems());
        boolean isUnique = Boolean.TRUE.equals(current.getUniqueItems());

        if (wasUnique != isUnique) {
            addConstraint(location, "uniqueItems", wasUnique, isUnique, isUnique, changes);
        }

        if (!Objects.equals(old.getPattern(), current.getPattern())) {
            addConstraint(
                    location,
                    "pattern",
                    old.getPattern(),
                    current.getPattern(),
                    current.getPattern() != null,
                    changes);
        }
    }

    private void compareNumberConstraint(
            ChangeLocation location,
            String name,
            Number oldValue,
            Number newValue,
            boolean higherIsTighter,
            List<DetectedChange> changes) {

        if (oldValue == null && newValue == null) {
            return;
        }

        if (oldValue == null || newValue == null) {
            // Introducing a constraint tightens the contract; dropping it relaxes it.
            addConstraint(location, name, oldValue, newValue, newValue != null, changes);
            return;
        }

        int comparison = toBigDecimal(oldValue).compareTo(toBigDecimal(newValue));

        if (comparison == 0) {
            return;
        }

        boolean tightened = higherIsTighter ? comparison < 0 : comparison > 0;
        addConstraint(location, name, oldValue, newValue, tightened, changes);
    }

    private void addConstraint(
            ChangeLocation location,
            String name,
            Object oldValue,
            Object newValue,
            boolean tightened,
            List<DetectedChange> changes) {

        changes.add(change(
                ChangeType.SCHEMA_CONSTRAINT_CHANGED,
                impact(location, tightened),
                location,
                "Schema constraint " + name + " at " + location.describe()
                        + (tightened ? " tightened" : " relaxed")
                        + " from " + describe(oldValue)
                        + " to " + describe(newValue) + "."));
    }


    private void compareAdditionalProperties(
            ComparisonContext context,
            ChangeLocation location,
            Schema<?> old,
            Schema<?> current,
            Set<SchemaPair> visited,
            List<DetectedChange> changes) {

        Object oldAdditional = old.getAdditionalProperties();
        Object newAdditional = current.getAdditionalProperties();

        boolean wasAllowed = allowsAdditionalProperties(oldAdditional);
        boolean isAllowed = allowsAdditionalProperties(newAdditional);

        if (wasAllowed != isAllowed) {
            changes.add(change(
                    ChangeType.SCHEMA_ADDITIONAL_PROPERTIES_CHANGED,
                    impact(location, !isAllowed),
                    location,
                    isAllowed
                            ? "Schema at " + location.describe()
                                    + " now allows additional properties."
                            : "Schema at " + location.describe()
                                    + " no longer allows additional properties."));
        }

        if (oldAdditional instanceof Schema<?> oldSchema
                && newAdditional instanceof Schema<?> newSchema) {
            changes.addAll(compare(
                    context,
                    propertyLocation(location, "additionalProperties"),
                    oldSchema,
                    newSchema,
                    visited));
        }
    }

    private void compareItems(
            ComparisonContext context,
            ChangeLocation location,
            Schema<?> old,
            Schema<?> current,
            Set<SchemaPair> visited,
            List<DetectedChange> changes) {

        Schema<?> oldItems = old.getItems();
        Schema<?> newItems = current.getItems();

        if (oldItems == null && newItems == null) {
            return;
        }

        changes.addAll(compare(
                context,
                elementLocation(location),
                oldItems,
                newItems,
                visited));
    }

    private void compareProperties(
            ComparisonContext context,
            ChangeLocation location,
            Schema<?> old,
            Schema<?> current,
            Set<SchemaPair> visited,
            List<DetectedChange> changes) {

        Map<String, Schema> oldProperties =
                old.getProperties() == null ? Map.of() : old.getProperties();
        Map<String, Schema> newProperties =
                current.getProperties() == null ? Map.of() : current.getProperties();

        compareRequiredProperties(location, old, current, changes);

        for (String property : oldProperties.keySet()) {
            if (!newProperties.containsKey(property)) {
                changes.add(change(
                        ChangeType.SCHEMA_PROPERTY_REMOVED,
                        CompatibilityImpact.BREAKING,
                        propertyLocation(location, property),
                        "Property " + property + " at " + location.describe()
                                + " was removed."));
            }
        }

        for (String property : newProperties.keySet()) {
            if (!oldProperties.containsKey(property)) {
                changes.add(change(
                        ChangeType.SCHEMA_PROPERTY_ADDED,
                        CompatibilityImpact.NON_BREAKING,
                        propertyLocation(location, property),
                        "Property " + property + " at " + location.describe()
                                + " was added."));
            }
        }

        for (String property : oldProperties.keySet()) {
            if (newProperties.containsKey(property)) {
                changes.addAll(compare(
                        context,
                        propertyLocation(location, property),
                        oldProperties.get(property),
                        newProperties.get(property),
                        visited));
            }
        }
    }


    private void compareRequiredProperties(
            ChangeLocation location,
            Schema<?> old,
            Schema<?> current,
            List<DetectedChange> changes) {

        List<String> oldRequired = old.getRequired() == null ? List.of() : old.getRequired();
        List<String> newRequired =
                current.getRequired() == null ? List.of() : current.getRequired();

        for (String property : newRequired) {
            if (!oldRequired.contains(property)) {
                changes.add(change(
                        ChangeType.SCHEMA_PROPERTY_REQUIREDNESS_ADDED,
                        impact(location, true),
                        propertyLocation(location, property),
                        "Property " + property + " at " + location.describe()
                                + " is now required."));
            }
        }

        for (String property : oldRequired) {
            if (!newRequired.contains(property)) {
                changes.add(change(
                        ChangeType.SCHEMA_PROPERTY_REQUIREDNESS_REMOVED,
                        impact(location, false),
                        propertyLocation(location, property),
                        "Property " + property + " at " + location.describe()
                                + " is no longer required."));
            }
        }
    }

    private Resolution resolve(
            OpenAPI openAPI,
            ReferenceResolver referenceResolver,
            Schema<?> schema,
            ChangeLocation location) {

        if (schema == null) {
            return new Resolution(true, null, List.of());
        }

        String reference = schema.get$ref();

        if (reference == null) {
            return new Resolution(true, schema, List.of());
        }

        Object resolved = referenceResolver.resolve(reference, openAPI);

        if (resolved instanceof Schema<?> resolvedSchema) {
            return new Resolution(true, resolvedSchema, List.of());
        }

        if (referenceResolver.isLocalReference(reference)) {
            return new Resolution(false, null, List.of(change(
                    ChangeType.SCHEMA_REFERENCE_UNRESOLVED,
                    CompatibilityImpact.UNKNOWN,
                    location,
                    "Schema reference " + reference + " at " + location.describe()
                            + " could not be resolved.")));
        }

        // External references are out of scope for the current comparison and are deferred.
        return new Resolution(false, null, List.of());
    }

    private static List<DetectedChange> unresolvedChanges(
            Resolution oldResolution,
            Resolution newResolution) {

        List<DetectedChange> changes = new ArrayList<>();
        changes.addAll(oldResolution.changes());
        changes.addAll(newResolution.changes());
        return List.copyOf(changes);
    }

    private static DetectedChange change(
            ChangeType type,
            CompatibilityImpact impact,
            ChangeLocation location,
            String message) {

        return new DetectedChange(type, impact, location, message);
    }

    private static CompatibilityImpact impact(ChangeLocation location, boolean tightened) {
        boolean breaking = location.context() == ChangeContext.RESPONSE ? !tightened : tightened;

        return breaking ? CompatibilityImpact.BREAKING : CompatibilityImpact.NON_BREAKING;
    }

    private static ChangeLocation propertyLocation(ChangeLocation location, String property) {
        String path = location.propertyPath();

        return location.withPropertyPath(path == null ? property : path + "." + property);
    }

    private static ChangeLocation elementLocation(ChangeLocation location) {
        String path = location.propertyPath();

        return location.withPropertyPath(path == null ? "[]" : path + "[]");
    }

    private static String describe(Object value) {
        return value == null ? "none" : value.toString();
    }

    private static BigDecimal toBigDecimal(Number value) {
        return new BigDecimal(value.toString());
    }

    private static boolean allowsAdditionalProperties(Object additionalProperties) {
        if (additionalProperties instanceof Boolean allowed) {
            return allowed;
        }

        return true;
    }

    private record Resolution(
            boolean comparable,
            Schema<?> schema,
            List<DetectedChange> changes) {
    }

    private record SchemaPair(Schema<?> oldSchema, Schema<?> newSchema) {

        @Override
        public boolean equals(Object other) {
            return other instanceof SchemaPair pair
                    && oldSchema == pair.oldSchema
                    && newSchema == pair.newSchema;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(oldSchema)
                    + System.identityHashCode(newSchema);
        }
    }
}

