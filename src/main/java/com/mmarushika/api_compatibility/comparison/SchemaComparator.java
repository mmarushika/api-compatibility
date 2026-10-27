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
import java.util.HashSet;
import java.util.LinkedHashMap;
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
        compareComposition(context, location, old, current, visited, changes);

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

        Map<String, Schema> oldProperties = effectiveProperties(context, old);
        Map<String, Schema> newProperties = effectiveProperties(context, current);

        compareRequiredProperties(context, location, old, current, oldProperties, newProperties, changes);

        for (String property : oldProperties.keySet()) {
            if (!newProperties.containsKey(property)) {
                Schema<?> oldProp = oldProperties.get(property);
                boolean isReadOnly = isReadOnly(oldProp);
                boolean isWriteOnly = isWriteOnly(oldProp);

                CompatibilityImpact impact = CompatibilityImpact.BREAKING;
                if (location.context() == ChangeContext.REQUEST && isReadOnly) {
                    impact = CompatibilityImpact.NON_BREAKING;
                } else if (location.context() == ChangeContext.RESPONSE && isWriteOnly) {
                    impact = CompatibilityImpact.NON_BREAKING;
                }

                changes.add(change(
                        ChangeType.SCHEMA_PROPERTY_REMOVED,
                        impact,
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
                Schema<?> oldProp = oldProperties.get(property);
                Schema<?> newProp = newProperties.get(property);

                // If readOnly in request or writeOnly in response, skip detailed sub-comparison or report non-breaking
                if (location.context() == ChangeContext.REQUEST && isReadOnly(oldProp) && isReadOnly(newProp)) {
                    continue;
                }
                if (location.context() == ChangeContext.RESPONSE && isWriteOnly(oldProp) && isWriteOnly(newProp)) {
                    continue;
                }

                changes.addAll(compare(
                        context,
                        propertyLocation(location, property),
                        oldProp,
                        newProp,
                        visited));
            }
        }
    }

    private void compareRequiredProperties(
            ComparisonContext context,
            ChangeLocation location,
            Schema<?> old,
            Schema<?> current,
            Map<String, Schema> oldProperties,
            Map<String, Schema> newProperties,
            List<DetectedChange> changes) {

        List<String> oldRequired = effectiveRequired(context, old);
        List<String> newRequired = effectiveRequired(context, current);

        for (String property : newRequired) {
            if (!oldRequired.contains(property)) {
                Schema<?> propSchema = newProperties.get(property);
                boolean isReadOnly = isReadOnly(propSchema);
                boolean isWriteOnly = isWriteOnly(propSchema);

                CompatibilityImpact impact = impact(location, true);
                if (location.context() == ChangeContext.REQUEST && isReadOnly) {
                    impact = CompatibilityImpact.NON_BREAKING;
                } else if (location.context() == ChangeContext.RESPONSE && isWriteOnly) {
                    impact = CompatibilityImpact.NON_BREAKING;
                }

                changes.add(change(
                        ChangeType.SCHEMA_PROPERTY_REQUIREDNESS_ADDED,
                        impact,
                        propertyLocation(location, property),
                        "Property " + property + " at " + location.describe()
                                + " is now required."));
            }
        }

        for (String property : oldRequired) {
            if (!newRequired.contains(property)) {
                Schema<?> propSchema = oldProperties.get(property);
                boolean isReadOnly = isReadOnly(propSchema);
                boolean isWriteOnly = isWriteOnly(propSchema);

                CompatibilityImpact impact = impact(location, false);
                if (location.context() == ChangeContext.REQUEST && isReadOnly) {
                    impact = CompatibilityImpact.NON_BREAKING;
                } else if (location.context() == ChangeContext.RESPONSE && isWriteOnly) {
                    impact = CompatibilityImpact.NON_BREAKING;
                }

                changes.add(change(
                        ChangeType.SCHEMA_PROPERTY_REQUIREDNESS_REMOVED,
                        impact,
                        propertyLocation(location, property),
                        "Property " + property + " at " + location.describe()
                                + " is no longer required."));
            }
        }
    }

    private void compareComposition(
            ComparisonContext context,
            ChangeLocation location,
            Schema<?> old,
            Schema<?> current,
            Set<SchemaPair> visited,
            List<DetectedChange> changes) {

        comparePolymorphic(context, location, "oneOf", old.getOneOf(), current.getOneOf(), visited, changes);
        comparePolymorphic(context, location, "anyOf", old.getAnyOf(), current.getAnyOf(), visited, changes);
        compareAllOf(context, location, old.getAllOf(), current.getAllOf(), visited, changes);
        compareNot(context, location, old.getNot(), current.getNot(), visited, changes);
    }

    private void comparePolymorphic(
            ComparisonContext context,
            ChangeLocation location,
            String operator,
            List<Schema> oldAlternatives,
            List<Schema> newAlternatives,
            Set<SchemaPair> visited,
            List<DetectedChange> changes) {

        List<Schema> oldList = oldAlternatives == null ? List.of() : oldAlternatives;
        List<Schema> newList = newAlternatives == null ? List.of() : newAlternatives;

        if (oldList.isEmpty() && newList.isEmpty()) {
            return;
        }

        Map<String, Schema> oldIndexed = indexAlternatives(oldList);
        Map<String, Schema> newIndexed = indexAlternatives(newList);

        for (Map.Entry<String, Schema> entry : oldIndexed.entrySet()) {
            String key = entry.getKey();
            if (!newIndexed.containsKey(key)) {
                ChangeLocation altLocation = compositionLocation(location, operator, key);
                changes.add(change(
                        ChangeType.SCHEMA_REMOVED,
                        impact(location, true),
                        altLocation,
                        "Schema alternative in " + operator + " at " + altLocation.describe() + " was removed."));
            }
        }

        for (Map.Entry<String, Schema> entry : newIndexed.entrySet()) {
            String key = entry.getKey();
            if (!oldIndexed.containsKey(key)) {
                ChangeLocation altLocation = compositionLocation(location, operator, key);
                changes.add(change(
                        ChangeType.SCHEMA_ADDED,
                        impact(location, false),
                        altLocation,
                        "Schema alternative in " + operator + " at " + altLocation.describe() + " was added."));
            }
        }

        for (Map.Entry<String, Schema> entry : oldIndexed.entrySet()) {
            String key = entry.getKey();
            Schema newAlt = newIndexed.get(key);
            if (newAlt != null) {
                ChangeLocation altLocation = compositionLocation(location, operator, key);
                changes.addAll(compare(context, altLocation, entry.getValue(), newAlt, visited));
            }
        }
    }

    private void compareAllOf(
            ComparisonContext context,
            ChangeLocation location,
            List<Schema> oldAllOf,
            List<Schema> newAllOf,
            Set<SchemaPair> visited,
            List<DetectedChange> changes) {

        List<Schema> oldList = oldAllOf == null ? List.of() : oldAllOf;
        List<Schema> newList = newAllOf == null ? List.of() : newAllOf;

        if (oldList.isEmpty() || newList.isEmpty()) {
            return;
        }

        Map<String, Schema> oldIndexed = indexAlternatives(oldList);
        Map<String, Schema> newIndexed = indexAlternatives(newList);

        // For allOf:
        // In REQUEST: adding allOf requirement tightens constraints (tightened=true -> BREAKING)
        // In RESPONSE: adding allOf requirement guarantees more (tightened=false -> NON_BREAKING)
        for (Map.Entry<String, Schema> entry : oldIndexed.entrySet()) {
            String key = entry.getKey();
            if (!newIndexed.containsKey(key)) {
                ChangeLocation altLocation = compositionLocation(location, "allOf", key);
                changes.add(change(
                        ChangeType.SCHEMA_REMOVED,
                        impact(location, false),
                        altLocation,
                        "Schema component in allOf at " + altLocation.describe() + " was removed."));
            }
        }

        for (Map.Entry<String, Schema> entry : newIndexed.entrySet()) {
            String key = entry.getKey();
            if (!oldIndexed.containsKey(key)) {
                ChangeLocation altLocation = compositionLocation(location, "allOf", key);
                changes.add(change(
                        ChangeType.SCHEMA_ADDED,
                        impact(location, true),
                        altLocation,
                        "Schema component in allOf at " + altLocation.describe() + " was added."));
            }
        }

        for (Map.Entry<String, Schema> entry : oldIndexed.entrySet()) {
            String key = entry.getKey();
            Schema newAlt = newIndexed.get(key);
            if (newAlt != null) {
                ChangeLocation altLocation = compositionLocation(location, "allOf", key);
                changes.addAll(compare(context, altLocation, entry.getValue(), newAlt, visited));
            }
        }
    }

    private void compareNot(
            ComparisonContext context,
            ChangeLocation location,
            Schema<?> oldNot,
            Schema<?> currentNot,
            Set<SchemaPair> visited,
            List<DetectedChange> changes) {

        if (oldNot == null && currentNot == null) {
            return;
        }

        ChangeLocation notLocation = location.withPropertyPath(
                location.propertyPath() == null ? "not" : location.propertyPath() + ".not");

        if (oldNot == null) {
            changes.add(change(
                    ChangeType.SCHEMA_ADDED,
                    impact(location, true),
                    notLocation,
                    "Schema restriction 'not' at " + notLocation.describe() + " was added."));
            return;
        }

        if (currentNot == null) {
            changes.add(change(
                    ChangeType.SCHEMA_REMOVED,
                    impact(location, false),
                    notLocation,
                    "Schema restriction 'not' at " + notLocation.describe() + " was removed."));
            return;
        }

        changes.addAll(compare(context, notLocation, oldNot, currentNot, visited));
    }

    private Map<String, Schema> indexAlternatives(List<Schema> alternatives) {
        Map<String, Schema> indexed = new LinkedHashMap<>();
        for (int i = 0; i < alternatives.size(); i++) {
            Schema schema = alternatives.get(i);
            String id;
            if (schema.get$ref() != null) {
                String ref = schema.get$ref();
                id = ref.substring(ref.lastIndexOf('/') + 1);
            } else if (schema.getTitle() != null && !schema.getTitle().isBlank()) {
                id = schema.getTitle();
            } else if (schema.getType() != null) {
                id = schema.getType();
            } else {
                id = String.valueOf(i);
            }
            if (indexed.containsKey(id)) {
                id = id + "_" + i;
            }
            indexed.put(id, schema);
        }
        return indexed;
    }

    private ChangeLocation compositionLocation(ChangeLocation location, String operator, String identifier) {
        String path = location.propertyPath();
        String sub = operator + "[" + identifier + "]";
        return location.withPropertyPath(path == null ? sub : path + "." + sub);
    }

    private Map<String, Schema> effectiveProperties(ComparisonContext context, Schema<?> schema) {
        Map<String, Schema> properties = new LinkedHashMap<>();
        collectProperties(context, schema, properties, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        return properties;
    }

    private void collectProperties(
            ComparisonContext context,
            Schema<?> schema,
            Map<String, Schema> target,
            Set<Schema<?>> visited) {
        if (schema == null || !visited.add(schema)) {
            return;
        }

        if (schema.getProperties() != null) {
            target.putAll(schema.getProperties());
        }

        if (schema.getAllOf() != null) {
            for (Schema<?> sub : schema.getAllOf()) {
                Schema<?> resolved = resolveSchema(context, sub);
                collectProperties(context, resolved, target, visited);
            }
        }
    }

    private List<String> effectiveRequired(ComparisonContext context, Schema<?> schema) {
        Set<String> required = new LinkedHashSet<>();
        collectRequired(context, schema, required, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        return new ArrayList<>(required);
    }

    private void collectRequired(
            ComparisonContext context,
            Schema<?> schema,
            Set<String> target,
            Set<Schema<?>> visited) {
        if (schema == null || !visited.add(schema)) {
            return;
        }

        if (schema.getRequired() != null) {
            target.addAll(schema.getRequired());
        }

        if (schema.getAllOf() != null) {
            for (Schema<?> sub : schema.getAllOf()) {
                Schema<?> resolved = resolveSchema(context, sub);
                collectRequired(context, resolved, target, visited);
            }
        }
    }

    private Schema<?> resolveSchema(ComparisonContext context, Schema<?> schema) {
        if (schema == null || schema.get$ref() == null) {
            return schema;
        }
        Object resolved = context.referenceResolver().resolve(schema.get$ref(), context.oldOpenAPI());
        if (resolved instanceof Schema<?> resolvedSchema) {
            return resolvedSchema;
        }
        resolved = context.referenceResolver().resolve(schema.get$ref(), context.newOpenAPI());
        if (resolved instanceof Schema<?> resolvedSchema) {
            return resolvedSchema;
        }
        return schema;
    }

    private boolean isReadOnly(Schema<?> schema) {
        return schema != null && Boolean.TRUE.equals(schema.getReadOnly());
    }

    private boolean isWriteOnly(Schema<?> schema) {
        return schema != null && Boolean.TRUE.equals(schema.getWriteOnly());
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

