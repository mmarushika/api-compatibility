package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.ComparisonContext;
import com.mmarushika.api_compatibility.model.DetectedChange;
import com.mmarushika.api_compatibility.resolver.ReferenceResolver;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SchemaComparatorTests {

    private final SchemaComparator schemaComparator = new SchemaComparator();

    @Test
    void reportsATypeChangeAsBreakingInBothDirections() {
        assertThat(compare(request(), new IntegerSchema(), new StringSchema()))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_TYPE_CHANGED, CompatibilityImpact.BREAKING));

        assertThat(compare(response(), new IntegerSchema(), new StringSchema()))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_TYPE_CHANGED, CompatibilityImpact.BREAKING));
    }

    @Test
    void reportsAFormatChangeAsBreaking() {
        assertThat(compare(request(), stringWithFormat("date"), stringWithFormat("date-time")))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_FORMAT_CHANGED, CompatibilityImpact.BREAKING));
    }

    @Test
    void classifiesNullableChangesByDirection() {
        assertThat(compare(request(), nullableString(), nonNullableString()))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_NULLABLE_CHANGED, CompatibilityImpact.BREAKING));

        assertThat(compare(response(), nullableString(), nonNullableString()))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_NULLABLE_CHANGED, CompatibilityImpact.NON_BREAKING));
    }

    @Test
    void classifiesEnumChangesByDirection() {
        assertThat(compare(request(), enumSchema("a"), enumSchema("a", "b")))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_ENUM_CHANGED, CompatibilityImpact.NON_BREAKING));

        assertThat(compare(response(), enumSchema("a"), enumSchema("a", "b")))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_ENUM_CHANGED, CompatibilityImpact.BREAKING));

        assertThat(compare(request(), enumSchema("a", "b"), enumSchema("a")))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_ENUM_CHANGED, CompatibilityImpact.BREAKING));
    }

    @Test
    void classifiesConstraintChangesByDirection() {
        assertThat(compare(request(), stringWithMinLength(3), stringWithMinLength(5)))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_CONSTRAINT_CHANGED, CompatibilityImpact.BREAKING));

        assertThat(compare(response(), stringWithMinLength(3), stringWithMinLength(5)))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_CONSTRAINT_CHANGED, CompatibilityImpact.NON_BREAKING));

        assertThat(compare(request(), stringWithMaxLength(5), stringWithMaxLength(8)))
                .extracting(DetectedChange::impact)
                .containsExactly(CompatibilityImpact.NON_BREAKING);
    }

    @Test
    void reportsPropertyRequirednessChangesWithAPropertyLocation() {
        DetectedChange change = compare(
                request(),
                objectWith("name", new StringSchema()),
                objectWithRequired("name", new StringSchema())).get(0);

        assertThat(change.type()).isEqualTo(ChangeType.SCHEMA_PROPERTY_REQUIREDNESS_ADDED);
        assertThat(change.impact()).isEqualTo(CompatibilityImpact.BREAKING);
        assertThat(change.location().propertyPath()).isEqualTo("name");

        assertThat(compare(
                response(),
                objectWith("name", new StringSchema()),
                objectWithRequired("name", new StringSchema())))
                .extracting(DetectedChange::impact)
                .containsExactly(CompatibilityImpact.NON_BREAKING);
    }

    @Test
    void reportsAddedAndRemovedProperties() {
        DetectedChange added = compare(
                request(),
                objectWith("name", new StringSchema()),
                objectOf("name", new StringSchema(), "nickname", new StringSchema())).get(0);

        assertThat(added.type()).isEqualTo(ChangeType.SCHEMA_PROPERTY_ADDED);
        assertThat(added.location().propertyPath()).isEqualTo("nickname");

        DetectedChange removed = compare(
                request(),
                objectOf("name", new StringSchema(), "nickname", new StringSchema()),
                objectWith("name", new StringSchema())).get(0);

        assertThat(removed.type()).isEqualTo(ChangeType.SCHEMA_PROPERTY_REMOVED);
        assertThat(removed.location().propertyPath()).isEqualTo("nickname");
    }

    @Test
    void reportsNestedPropertyChangesWithTheFullPropertyPath() {
        DetectedChange change = compare(
                request(),
                objectWith("address", objectWith("zip", new IntegerSchema())),
                objectWith("address", objectWith("zip", new StringSchema()))).get(0);

        assertThat(change.type()).isEqualTo(ChangeType.SCHEMA_TYPE_CHANGED);
        assertThat(change.location().propertyPath()).isEqualTo("address.zip");
    }

    @Test
    void reportsArrayItemChangesUnderTheElementLocation() {
        DetectedChange change = compare(
                request(), arrayOf(new IntegerSchema()), arrayOf(new StringSchema())).get(0);

        assertThat(change.type()).isEqualTo(ChangeType.SCHEMA_TYPE_CHANGED);
        assertThat(change.location().propertyPath()).isEqualTo("[]");
    }

    @Test
    void classifiesAdditionalPropertiesByDirection() {
        ObjectSchema closed = new ObjectSchema();
        closed.setAdditionalProperties(Boolean.FALSE);

        assertThat(compare(request(), new ObjectSchema(), closed))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_ADDITIONAL_PROPERTIES_CHANGED,
                        CompatibilityImpact.BREAKING));

        assertThat(compare(response(), new ObjectSchema(), closed))
                .extracting(DetectedChange::impact)
                .containsExactly(CompatibilityImpact.NON_BREAKING);
    }

    @Test
    void resolvesReferencesBeforeComparing() {
        ComparisonContext context = new ComparisonContext(
                new OpenAPI().components(
                        new Components().addSchemas("Payload", new IntegerSchema())),
                new OpenAPI().components(
                        new Components().addSchemas("Payload", new StringSchema())),
                new ReferenceResolver());

        Schema<?> reference = new Schema<>().$ref("#/components/schemas/Payload");

        assertThat(schemaComparator.compare(context, request(), reference, reference))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.SCHEMA_TYPE_CHANGED);
    }

    @Test
    void reportsADanglingReferenceAsUnresolvedRatherThanAddedOrRemoved() {
        Schema<?> dangling = new Schema<>().$ref("#/components/schemas/Missing");

        assertThat(compare(request(), new StringSchema(), dangling))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_REFERENCE_UNRESOLVED, CompatibilityImpact.UNKNOWN));

        assertThat(compare(request(), dangling, new StringSchema()))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.SCHEMA_REFERENCE_UNRESOLVED);
    }

    @Test
    void defersExternalReferences() {
        Schema<?> external = new Schema<>().$ref("common.yaml#/components/schemas/Payload");

        assertThat(compare(request(), new StringSchema(), external)).isEmpty();
        assertThat(compare(request(), external, new StringSchema())).isEmpty();
    }

    @Test
    void terminatesOnASelfReferencingSchema() {
        ObjectSchema self = new ObjectSchema();
        self.addProperties("child", self);

        assertThat(compare(request(), self, self)).isEmpty();
    }

    @Test
    void mergesAllOfPropertiesWhenComparingObjects() {
        ObjectSchema base = new ObjectSchema();
        base.addProperties("id", new IntegerSchema());

        ObjectSchema ext = new ObjectSchema();
        ext.addAllOfItem(base);
        ext.addProperties("name", new StringSchema());

        ObjectSchema full = new ObjectSchema();
        full.addProperties("id", new IntegerSchema());
        full.addProperties("name", new StringSchema());

        assertThat(compare(request(), ext, full)).isEmpty();
    }

    @Test
    void reportsOneOfAlternativeRemovalAsBreakingInRequest() {
        Schema<?> oldOneOf = new Schema<>().addOneOfItem(new StringSchema()).addOneOfItem(new IntegerSchema());
        Schema<?> newOneOf = new Schema<>().addOneOfItem(new StringSchema());

        assertThat(compare(request(), oldOneOf, newOneOf))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .contains(tuple(ChangeType.SCHEMA_REMOVED, CompatibilityImpact.BREAKING));
    }

    @Test
    void reportsOneOfAlternativeAdditionAsBreakingInResponse() {
        Schema<?> oldOneOf = new Schema<>().addOneOfItem(new StringSchema());
        Schema<?> newOneOf = new Schema<>().addOneOfItem(new StringSchema()).addOneOfItem(new IntegerSchema());

        assertThat(compare(response(), oldOneOf, newOneOf))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .contains(tuple(ChangeType.SCHEMA_ADDED, CompatibilityImpact.BREAKING));
    }

    @Test
    void ignoresReadOnlyPropertyRemovalInRequest() {
        StringSchema readOnlyId = new StringSchema();
        readOnlyId.setReadOnly(true);

        ObjectSchema oldObj = new ObjectSchema();
        oldObj.addProperties("id", readOnlyId);

        ObjectSchema newObj = new ObjectSchema();

        assertThat(compare(request(), oldObj, newObj))
                .extracting(DetectedChange::impact)
                .containsOnly(CompatibilityImpact.NON_BREAKING);
    }

    @Test
    void ignoresWriteOnlyPropertyRemovalInResponse() {
        StringSchema writeOnlyPass = new StringSchema();
        writeOnlyPass.setWriteOnly(true);

        ObjectSchema oldObj = new ObjectSchema();
        oldObj.addProperties("password", writeOnlyPass);

        ObjectSchema newObj = new ObjectSchema();

        assertThat(compare(response(), oldObj, newObj))
                .extracting(DetectedChange::impact)
                .containsOnly(CompatibilityImpact.NON_BREAKING);
    }

    private List<DetectedChange> compare(
            ChangeLocation location, Schema<?> oldSchema, Schema<?> newSchema) {

        return schemaComparator.compare(context(), location, oldSchema, newSchema);
    }

    private static ComparisonContext context() {
        return new ComparisonContext(new OpenAPI(), new OpenAPI(), new ReferenceResolver());
    }

    private static ChangeLocation request() {
        return ChangeLocation.forRequestBody("/users", "POST");
    }

    private static ChangeLocation response() {
        return ChangeLocation.forResponse("/users", "GET", "200");
    }

    private static Schema<?> stringWithFormat(String format) {
        StringSchema schema = new StringSchema();
        schema.setFormat(format);
        return schema;
    }

    private static Schema<?> stringWithMinLength(int minLength) {
        StringSchema schema = new StringSchema();
        schema.setMinLength(minLength);
        return schema;
    }

    private static Schema<?> stringWithMaxLength(int maxLength) {
        StringSchema schema = new StringSchema();
        schema.setMaxLength(maxLength);
        return schema;
    }

    private static Schema<?> nullableString() {
        StringSchema schema = new StringSchema();
        schema.setNullable(true);
        return schema;
    }

    private static Schema<?> nonNullableString() {
        StringSchema schema = new StringSchema();
        schema.setNullable(false);
        return schema;
    }

    private static Schema<?> enumSchema(String... values) {
        StringSchema schema = new StringSchema();
        schema.setEnum(List.of(values));
        return schema;
    }

    private static ObjectSchema objectWith(String name, Schema<?> property) {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperties(name, property);
        return schema;
    }

    private static ObjectSchema objectOf(
            String firstName, Schema<?> first, String secondName, Schema<?> second) {

        ObjectSchema schema = objectWith(firstName, first);
        schema.addProperties(secondName, second);
        return schema;
    }

    private static ObjectSchema objectWithRequired(String name, Schema<?> property) {
        ObjectSchema schema = objectWith(name, property);
        schema.setRequired(List.of(name));
        return schema;
    }

    private static ArraySchema arrayOf(Schema<?> items) {
        ArraySchema schema = new ArraySchema();
        schema.setItems(items);
        return schema;
    }
}
