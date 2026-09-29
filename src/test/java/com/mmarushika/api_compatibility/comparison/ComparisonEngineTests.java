package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.DetectedChange;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIV3Parser;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class ComparisonEngineTests {

    private static final Path SAMPLE_OLD_SPEC = Path.of(
            "src/main/java/com/mmarushika/api_compatibility/sample/sample_spec.json");
    private static final Path SAMPLE_NEW_SPEC = Path.of(
            "src/main/java/com/mmarushika/api_compatibility/sample/sample_spec2.json");

    @Autowired
    private ComparisonEngine engine;

    @Test
    void reportsNoChangesForIdenticalDocuments() {
        OpenAPI oldSpec = spec("/users", new PathItem().get(new Operation()));

        OpenAPI newSpec = spec("/users", new PathItem().get(new Operation()));

        assertThat(engine.compare(oldSpec, newSpec)).isEmpty();
    }

    @Test
    void reportsChangesFoundInThePathsOfBothDocuments() {
        OpenAPI oldSpec = spec("/users", new PathItem().get(new Operation()).post(new Operation()));

        OpenAPI newSpec = spec("/users", new PathItem().get(new Operation()));

        assertThat(engine.compare(oldSpec, newSpec))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.OPERATION_REMOVED);
    }

    @Test
    void treatsDocumentsWithoutPathsAsEmpty() {
        assertThat(engine.compare(new OpenAPI(), new OpenAPI())).isEmpty();
        assertThat(engine.compare(null, null)).isEmpty();

        assertThat(engine.compare(null, spec("/users", new PathItem().get(new Operation()))))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.PATH_ADDED);
    }

    /**
     * End to end check against the parser: the older sample documents {@code GET /} and
     * {@code POST /} plus {@code /v2}, the newer one documents only {@code GET /}, so the
     * comparison must report the removed operation before the removed path (old document order).
     * Requires the project to be run from its root, so it is skipped rather than failed when the
     * samples are not reachable.
     */
    @Test
    void reportsChangesFromParsedSampleDocuments() {
        assumeTrue(Files.exists(SAMPLE_OLD_SPEC) && Files.exists(SAMPLE_NEW_SPEC),
                "sample specifications must be reachable from the working directory");

        OpenAPI oldSpec = new OpenAPIV3Parser().readLocation(SAMPLE_OLD_SPEC.toString(), null, null).getOpenAPI();
        OpenAPI newSpec = new OpenAPIV3Parser().readLocation(SAMPLE_NEW_SPEC.toString(), null, null).getOpenAPI();

        assertThat(engine.compare(oldSpec, newSpec)).containsExactly(
                new DetectedChange(
                        ChangeType.OPERATION_REMOVED,
                        CompatibilityImpact.BREAKING,
                        ChangeLocation.forOperation("/", "POST"),
                        "Operation POST / was removed."),
                new DetectedChange(
                        ChangeType.PATH_REMOVED,
                        CompatibilityImpact.BREAKING,
                        ChangeLocation.forPath("/v2"),
                        "Path '/v2' was removed."));
    }

    @Test
    void reportsARequestSchemaChangeReachableFromTheEngine() {
        OpenAPI oldSpec = specWithRequestBody(new IntegerSchema());
        OpenAPI newSpec = specWithRequestBody(new StringSchema());

        assertThat(engine.compare(oldSpec, newSpec))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_TYPE_CHANGED, CompatibilityImpact.BREAKING));
    }

    @Test
    void reportsAResponseSchemaChangeReachableFromTheEngine() {
        OpenAPI oldSpec = specWithResponseSchema(new IntegerSchema());
        OpenAPI newSpec = specWithResponseSchema(new StringSchema());

        assertThat(engine.compare(oldSpec, newSpec))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.SCHEMA_TYPE_CHANGED, CompatibilityImpact.BREAKING));
    }

    @Test
    void reportsASchemaChangeNestedInsideAParsedDocumentReachableFromTheEngine() {
        OpenAPI oldSpec = specWithRequestBody(objectWithProperty("name", new IntegerSchema()));
        OpenAPI newSpec = specWithRequestBody(objectWithProperty("name", new StringSchema()));

        DetectedChange change = engine.compare(oldSpec, newSpec).get(0);

        assertThat(change.type()).isEqualTo(ChangeType.SCHEMA_TYPE_CHANGED);
        assertThat(change.location().propertyPath()).isEqualTo("name");
    }

    private static OpenAPI specWithRequestBody(Schema<?> schema) {
        Operation operation = new Operation().requestBody(new RequestBody().content(
                new Content().addMediaType("application/json", new MediaType().schema(schema))));

        return spec("/users", new PathItem().post(operation));
    }

    private static OpenAPI specWithResponseSchema(Schema<?> schema) {
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse(
                "200",
                new ApiResponse().content(new Content().addMediaType(
                        "application/json", new MediaType().schema(schema)))));

        return spec("/users", new PathItem().get(operation));
    }

    private static Schema<?> objectWithProperty(String name, Schema<?> property) {
        ObjectSchema object = new ObjectSchema();
        object.addProperties(name, property);
        return object;
    }

    private static OpenAPI spec(String path, PathItem pathItem) {
        return new OpenAPI().paths(new Paths().addPathItem(path, pathItem));
    }
}
