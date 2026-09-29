package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.ComparisonContext;
import com.mmarushika.api_compatibility.model.DetectedChange;
import com.mmarushika.api_compatibility.resolver.ReferenceResolver;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ContentComparatorTests {

    private final ContentComparator contentComparator =
            new ContentComparator(new SchemaComparator());

    @Test
    void reportsRequestMediaTypesThatAppearOrDisappear() {
        Content json = json(new StringSchema());
        Content jsonAndXml = jsonAndXml();

        ChangeLocation location = ChangeLocation.forRequestBody("/users", "POST");

        assertThat(contentComparator.compare(context(), location, json, jsonAndXml))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.REQUEST_MEDIA_TYPE_ADDED, CompatibilityImpact.NON_BREAKING));

        assertThat(contentComparator.compare(context(), location, jsonAndXml, json))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.REQUEST_MEDIA_TYPE_REMOVED, CompatibilityImpact.BREAKING));
    }

    @Test
    void reportsResponseMediaTypesThatAppearOrDisappear() {
        Content json = json(new StringSchema());
        Content jsonAndXml = jsonAndXml();

        ChangeLocation location = ChangeLocation.forResponse("/users", "GET", "200");

        assertThat(contentComparator.compare(context(), location, json, jsonAndXml))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.RESPONSE_MEDIA_TYPE_ADDED, CompatibilityImpact.NON_BREAKING));

        assertThat(contentComparator.compare(context(), location, jsonAndXml, json))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.RESPONSE_MEDIA_TYPE_REMOVED, CompatibilityImpact.BREAKING));
    }

    @Test
    void comparesTheSchemasOfSharedMediaTypes() {
        DetectedChange change = contentComparator.compare(
                context(),
                ChangeLocation.forResponse("/users", "GET", "200"),
                json(new IntegerSchema()),
                json(new StringSchema())).get(0);

        assertThat(change.type()).isEqualTo(ChangeType.SCHEMA_TYPE_CHANGED);
        assertThat(change.location().mediaType()).isEqualTo("application/json");
        assertThat(change.location().statusCode()).isEqualTo("200");
    }

    @Test
    void reportsMediaTypeLevelLocationsWhenAMediaTypeDisappears() {
        DetectedChange change = contentComparator.compare(
                context(),
                ChangeLocation.forRequestBody("/users", "POST"),
                jsonAndXml(),
                json(new StringSchema())).get(0);

        assertThat(change.type()).isEqualTo(ChangeType.REQUEST_MEDIA_TYPE_REMOVED);
        assertThat(change.location().mediaType()).isEqualTo("application/xml");
    }

    @Test
    void treatsMissingContentAsEmpty() {
        assertThat(contentComparator.compare(
                context(), ChangeLocation.forRequestBody("/users", "POST"), null, null)).isEmpty();
    }

    private static ComparisonContext context() {
        return new ComparisonContext(new OpenAPI(), new OpenAPI(), new ReferenceResolver());
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
