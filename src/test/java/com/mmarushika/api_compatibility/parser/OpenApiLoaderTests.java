package com.mmarushika.api_compatibility.parser;

import com.mmarushika.api_compatibility.comparison.ComparisonEngine;
import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.DetectedChange;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
class OpenApiLoaderTests {

    private static final String PAGE_PARAMETER =
            "    PageParam:\n"
                    + "      name: page\n"
                    + "      in: query\n"
                    + "      required: false\n"
                    + "      schema:\n"
                    + "        type: integer";
    private static final String REQUIRED_PAGE_PARAMETER =
            "    RequiredPageParam:\n"
                    + "      name: page\n"
                    + "      in: query\n"
                    + "      required: true\n"
                    + "      schema:\n"
                    + "        type: integer";
    private static final String ID_PARAMETER =
            "    IdParam:\n"
                    + "      name: id\n"
                    + "      in: path\n"
                    + "      required: true\n"
                    + "      schema:\n"
                    + "        type: string";

    private static final String PATH_PAGE_REF =
            "      - $ref: '#/components/parameters/PageParam'";
    private static final String OPERATION_PAGE_REF =
            "        - $ref: '#/components/parameters/PageParam'";
    private static final String OPERATION_REQUIRED_PAGE_REF =
            "        - $ref: '#/components/parameters/RequiredPageParam'";
    private static final String OPERATION_ID_REF =
            "        - $ref: '#/components/parameters/IdParam'";
    private static final String OPERATION_MISSING_REF =
            "        - $ref: '#/components/parameters/MissingParam'";
    private static final String OPERATION_DIRECT_REQUIRED_PAGE =
            "        - name: page\n"
                    + "          in: query\n"
                    + "          required: true\n"
                    + "          schema:\n"
                    + "            type: integer";

    @Autowired
    private OpenApiLoader loader;

    @Autowired
    private ComparisonEngine engine;

    @Test
    void keepsReferencedParametersUnresolvedWhenLoading() {
        // The loader only deserializes; the comparison layer resolves references.
        OpenAPI document = loader.loadContents(
                document(PATH_PAGE_REF, OPERATION_ID_REF, PAGE_PARAMETER + "\n" + ID_PARAMETER));

        PathItem pathItem = document.getPaths().get("/users");

        assertThat(pathItem.getParameters()).hasSize(1);
        assertThat(pathItem.getParameters().get(0).getName()).isNull();
        assertThat(pathItem.getParameters().get(0).get$ref())
                .isEqualTo("#/components/parameters/PageParam");

        assertThat(pathItem.getGet().getParameters()).hasSize(1);
        assertThat(pathItem.getGet().getParameters().get(0).getName()).isNull();
        assertThat(pathItem.getGet().getParameters().get(0).get$ref())
                .isEqualTo("#/components/parameters/IdParam");
    }

    @Test
    void keepsPathLevelParametersAtThePathItem() {
        // The parser's own resolver would move the path parameter into the operation; the loader
        // keeps both levels separate so the comparison can apply the override rule.
        OpenAPI document = loader.loadContents(
                document(PATH_PAGE_REF, OPERATION_ID_REF, PAGE_PARAMETER + "\n" + ID_PARAMETER));

        PathItem pathItem = document.getPaths().get("/users");

        assertThat(pathItem.getParameters()).hasSize(1);
        assertThat(pathItem.getGet().getParameters()).hasSize(1);
    }

    @Test
    void leavesDirectParametersUntouched() {
        OpenAPI document = loader.loadContents(
                document("", OPERATION_DIRECT_REQUIRED_PAGE, PAGE_PARAMETER));

        Parameter parameter = document.getPaths().get("/users").getGet().getParameters().get(0);

        assertThat(parameter.getName()).isEqualTo("page");
        assertThat(parameter.getIn()).isEqualTo("query");
        assertThat(parameter.get$ref()).isNull();
    }

    @Test
    void letsAnOperationLevelParameterOverrideAPathLevelReference() {
        OpenAPI optionalPage = loader.loadContents(document(PATH_PAGE_REF, "", PAGE_PARAMETER));
        OpenAPI requiredPage = loader.loadContents(
                document(PATH_PAGE_REF, OPERATION_DIRECT_REQUIRED_PAGE, PAGE_PARAMETER));

        assertThat(engine.compare(optionalPage, requiredPage)).containsExactly(
                new DetectedChange(
                        ChangeType.PARAMETER_REQUIREDNESS_ADDED,
                        CompatibilityImpact.BREAKING,
                        ChangeLocation.forParameter("/users", "GET", "page", "query"),
                        "Parameter query.page of GET /users was optional and is now required."));
    }

    @Test
    void letsAnOperationLevelReferenceOverrideAPathLevelReference() {
        String components = PAGE_PARAMETER + "\n" + REQUIRED_PAGE_PARAMETER;

        OpenAPI optionalPage = loader.loadContents(
                document(PATH_PAGE_REF, OPERATION_PAGE_REF, components));
        OpenAPI requiredPage = loader.loadContents(
                document(PATH_PAGE_REF, OPERATION_REQUIRED_PAGE_REF, components));

        assertThat(engine.compare(optionalPage, requiredPage))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.PARAMETER_REQUIREDNESS_ADDED, CompatibilityImpact.BREAKING));
    }

    @Test
    void reportsARequirednessChangeOfAReferencedParameter() {
        String components = PAGE_PARAMETER + "\n" + REQUIRED_PAGE_PARAMETER;

        OpenAPI optionalPage = loader.loadContents(document("", OPERATION_PAGE_REF, components));
        OpenAPI requiredPage =
                loader.loadContents(document("", OPERATION_REQUIRED_PAGE_REF, components));

        assertThat(engine.compare(optionalPage, requiredPage)).containsExactly(
                new DetectedChange(
                        ChangeType.PARAMETER_REQUIREDNESS_ADDED,
                        CompatibilityImpact.BREAKING,
                        ChangeLocation.forParameter("/users", "GET", "page", "query"),
                        "Parameter query.page of GET /users was optional and is now required."));
    }

    @Test
    void ignoresAReferenceThatCannotBeResolvedLocally() {
        OpenAPI withoutParameter = loader.loadContents(document("", "", PAGE_PARAMETER));
        OpenAPI withDanglingReference =
                loader.loadContents(document("", OPERATION_MISSING_REF, PAGE_PARAMETER));

        // A reference is not a parameter name, so a dangling one does not become an added parameter.
        assertThat(engine.compare(withoutParameter, withDanglingReference)).isEmpty();
    }

    @Test
    void loadsADocumentFromItsLocationWithoutResolvingReferences(@TempDir Path directory)
            throws IOException {

        Path spec = directory.resolve("openapi.yaml");
        Files.writeString(
                spec,
                document(PATH_PAGE_REF, OPERATION_ID_REF, PAGE_PARAMETER + "\n" + ID_PARAMETER));

        OpenAPI document = loader.load(spec.toString());

        assertThat(document.getPaths().get("/users").getParameters().get(0).get$ref())
                .isEqualTo("#/components/parameters/PageParam");
    }

    private static String document(
            String pathParameters,
            String operationParameters,
            String components) {

        return "openapi: 3.0.0\n"
                + "info:\n"
                + "  title: Test\n"
                + "  version: 1.0.0\n"
                + "paths:\n"
                + "  /users:\n"
                + "    parameters:\n"
                + pathParameters + "\n"
                + "    get:\n"
                + "      parameters:\n"
                + operationParameters + "\n"
                + "      responses:\n"
                + "        '200':\n"
                + "          description: ok\n"
                + "components:\n"
                + "  parameters:\n"
                + components + "\n";
    }
}
