package com.mmarushika.api_compatibility.comparison;

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
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ResponseComparatorTests {

    private final ResponseComparator responseComparator =
            new ResponseComparator(new ContentComparator(new SchemaComparator()));

    @Test
    void reportsAddedAndRemovedResponses() {
        assertThat(compare(
                responses("200", jsonResponse(new StringSchema())),
                responsesWith("200", "201")))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(ChangeType.RESPONSE_ADDED, CompatibilityImpact.NON_BREAKING));

        assertThat(compare(
                responsesWith("200", "201"),
                responses("200", jsonResponse(new StringSchema()))))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(ChangeType.RESPONSE_REMOVED, CompatibilityImpact.BREAKING));
    }

    @Test
    void reportsMediaTypeChangesInsideAResponse() {
        assertThat(compare(
                responses("200", jsonResponse(new StringSchema())),
                responses("200", jsonAndXmlResponse())))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.RESPONSE_MEDIA_TYPE_ADDED, CompatibilityImpact.NON_BREAKING));

        assertThat(compare(
                responses("200", jsonAndXmlResponse()),
                responses("200", jsonResponse(new StringSchema()))))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.RESPONSE_MEDIA_TYPE_REMOVED, CompatibilityImpact.BREAKING));
    }

    @Test
    void reportsSchemaChangesInsideAResponse() {
        DetectedChange change = compare(
                responses("200", jsonResponse(new IntegerSchema())),
                responses("200", jsonResponse(new StringSchema()))).get(0);

        assertThat(change.type()).isEqualTo(ChangeType.SCHEMA_TYPE_CHANGED);
        assertThat(change.impact()).isEqualTo(CompatibilityImpact.BREAKING);
        assertThat(change.location().statusCode()).isEqualTo("200");
        assertThat(change.location().mediaType()).isEqualTo("application/json");
    }

    @Test
    void resolvesAReferencedResponseBeforeComparing() {
        ComparisonContext context = new ComparisonContext(
                new OpenAPI().components(new Components().addResponses(
                        "Ok", jsonResponse(new IntegerSchema()))),
                new OpenAPI().components(new Components().addResponses(
                        "Ok", jsonResponse(new StringSchema()))),
                new ReferenceResolver());

        assertThat(responseComparator.compare(
                context, "/users", "GET", responses("200", reference()), responses("200", reference())))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.SCHEMA_TYPE_CHANGED);
    }

    @Test
    void treatsADanglingResponseReferenceAsUnresolvedRatherThanRemoved() {
        assertThat(compare(responses("200", reference()), responses("200", jsonResponse(new StringSchema()))))
                .isEmpty();
        assertThat(compare(responses("200", jsonResponse(new StringSchema())), responses("200", reference())))
                .isEmpty();
    }

    @Test
    void treatsMissingResponsesAsEmpty() {
        assertThat(compare(null, null)).isEmpty();
    }

    private List<DetectedChange> compare(ApiResponses oldResponses, ApiResponses newResponses) {
        return responseComparator.compare(context(), "/users", "GET", oldResponses, newResponses);
    }

    private static ComparisonContext context() {
        return new ComparisonContext(new OpenAPI(), new OpenAPI(), new ReferenceResolver());
    }

    private static ApiResponses responses(String statusCode, ApiResponse response) {
        return new ApiResponses().addApiResponse(statusCode, response);
    }

    private static ApiResponses responsesWith(String first, String second) {
        return responses(first, jsonResponse(new StringSchema()))
                .addApiResponse(second, jsonResponse(new StringSchema()));
    }

    private static ApiResponse reference() {
        ApiResponse reference = new ApiResponse();
        reference.set$ref("#/components/responses/Ok");
        return reference;
    }

    private static ApiResponse jsonResponse(Schema<?> schema) {
        return new ApiResponse().content(
                new Content().addMediaType("application/json", new MediaType().schema(schema)));
    }

    private static ApiResponse jsonAndXmlResponse() {
        return new ApiResponse().content(new Content()
                .addMediaType("application/json", new MediaType().schema(new StringSchema()))
                .addMediaType("application/xml", new MediaType().schema(new StringSchema())));
    }
}
