package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.ComparisonContext;
import com.mmarushika.api_compatibility.model.DetectedChange;
import com.mmarushika.api_compatibility.resolver.ReferenceResolver;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RequestBodyComparatorTests {

    private final RequestBodyComparator requestBodyComparator =
            new RequestBodyComparator(new ContentComparator(new SchemaComparator()));

    @Test
    void reportsNoChangesWhenNeitherOperationDeclaresABody() {
        assertThat(compare(null, null)).isEmpty();
    }

    @Test
    void reportsAnAddedOptionalBodyAsNonBreaking() {
        assertThat(compare(null, body(false).content(json(new StringSchema()))))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.REQUEST_BODY_ADDED, CompatibilityImpact.NON_BREAKING));
    }

    @Test
    void reportsAnAddedRequiredBodyAsBreaking() {
        assertThat(compare(null, body(true).content(json(new StringSchema()))))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.REQUEST_BODY_ADDED, CompatibilityImpact.BREAKING));
    }

    @Test
    void reportsARemovedBodyAsBreaking() {
        assertThat(compare(body(true), null))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.REQUEST_BODY_REMOVED, CompatibilityImpact.BREAKING));
    }

    @Test
    void reportsRequirednessChanges() {
        assertThat(compare(body(false), body(true)))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.REQUEST_BODY_REQUIREDNESS_ADDED,
                        CompatibilityImpact.BREAKING));

        assertThat(compare(body(true), body(false)))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.REQUEST_BODY_REQUIREDNESS_REMOVED,
                        CompatibilityImpact.NON_BREAKING));
    }

    @Test
    void reportsMediaTypeChangesInsideABody() {
        assertThat(compare(
                body(true).content(json(new StringSchema())),
                body(true).content(jsonAndXml())))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.REQUEST_MEDIA_TYPE_ADDED, CompatibilityImpact.NON_BREAKING));

        assertThat(compare(
                body(true).content(jsonAndXml()),
                body(true).content(json(new StringSchema()))))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.REQUEST_MEDIA_TYPE_REMOVED, CompatibilityImpact.BREAKING));
    }

    @Test
    void reportsSchemaChangesInsideABody() {
        assertThat(compare(
                body(true).content(json(new IntegerSchema())),
                body(true).content(json(new StringSchema()))))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_TYPE_CHANGED, CompatibilityImpact.BREAKING));
    }

    @Test
    void resolvesAReferencedBodyBeforeComparing() {
        ComparisonContext context = new ComparisonContext(
                new OpenAPI().components(new Components().addRequestBodies(
                        "Payload", body(true).content(json(new IntegerSchema())))),
                new OpenAPI().components(new Components().addRequestBodies(
                        "Payload", body(true).content(json(new StringSchema())))),
                new ReferenceResolver());

        assertThat(requestBodyComparator.compare(
                context, "/users", "POST", reference(), reference()))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.SCHEMA_TYPE_CHANGED);
    }

    @Test
    void treatsADanglingBodyReferenceAsUnresolvedRatherThanAddedOrRemoved() {
        assertThat(compare(reference(), body(true).content(json(new StringSchema())))).isEmpty();
        assertThat(compare(body(true).content(json(new StringSchema())), reference())).isEmpty();
    }

    @Test
    void recordsTheRequestBodyLocation() {
        DetectedChange change = compare(null, body(true).content(json(new StringSchema()))).get(0);

        assertThat(change.location()).isEqualTo(ChangeLocation.forRequestBody("/users", "POST"));
    }

    private List<DetectedChange> compare(RequestBody oldBody, RequestBody newBody) {
        return requestBodyComparator.compare(context(), "/users", "POST", oldBody, newBody);
    }

    private static ComparisonContext context() {
        return new ComparisonContext(new OpenAPI(), new OpenAPI(), new ReferenceResolver());
    }

    private static RequestBody reference() {
        RequestBody reference = new RequestBody();
        reference.set$ref("#/components/requestBodies/Payload");
        return reference;
    }

    private static RequestBody body(boolean required) {
        return new RequestBody().required(required);
    }

    private static Content json(Schema<?> schema) {
        return new Content().addMediaType("application/json", new MediaType().schema(schema));
    }

    private static Content jsonAndXml() {
        return new Content()
                .addMediaType("application/json", new MediaType().schema(new StringSchema()))
                .addMediaType("application/xml", new MediaType().schema(new StringSchema()));
    }
}
