package com.mmarushika.api_compatibility.resolver;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceResolverTests {

    private final ReferenceResolver resolver = new ReferenceResolver();

    @Test
    void recognisesOnlyLocalPointers() {
        assertThat(resolver.isLocalReference("#/components/schemas/Pet")).isTrue();
        assertThat(resolver.isLocalReference("common.yaml#/components/schemas/Pet")).isFalse();
        assertThat(resolver.isLocalReference(null)).isFalse();
    }

    @Test
    void resolvesALocalSchemaReference() {
        StringSchema pet = new StringSchema();
        OpenAPI document = document(new Components().addSchemas("Pet", pet));

        assertThat(resolver.resolve("#/components/schemas/Pet", document)).isSameAs(pet);
    }

    @Test
    void resolvesANestedSchemaReference() {
        StringSchema pet = new StringSchema();
        Schema<?> alias = new Schema<>().$ref("#/components/schemas/Pet");
        OpenAPI document = document(
                new Components().addSchemas("Pet", pet).addSchemas("Alias", alias));

        assertThat(resolver.resolve("#/components/schemas/Alias", document)).isSameAs(pet);
    }

    @Test
    void resolvesAParameterReference() {
        Parameter page = new Parameter().name("page").in("query");
        OpenAPI document = document(new Components().addParameters("Page", page));

        assertThat(resolver.resolve("#/components/parameters/Page", document)).isSameAs(page);
    }

    @Test
    void resolvesARequestBodyReference() {
        RequestBody payload = new RequestBody();
        OpenAPI document = document(new Components().addRequestBodies("Payload", payload));

        assertThat(resolver.resolve("#/components/requestBodies/Payload", document))
                .isSameAs(payload);
    }

    @Test
    void resolvesAResponseReference() {
        ApiResponse ok = new ApiResponse().description("ok");
        OpenAPI document = document(new Components().addResponses("Ok", ok));

        assertThat(resolver.resolve("#/components/responses/Ok", document)).isSameAs(ok);
    }

    @Test
    void returnsNullForADanglingLocalReference() {
        OpenAPI document = document(new Components().addSchemas("Pet", new StringSchema()));

        assertThat(resolver.resolve("#/components/schemas/Missing", document)).isNull();
    }

    @Test
    void returnsNullForAnExternalReference() {
        OpenAPI document = document(new Components().addSchemas("Pet", new StringSchema()));

        assertThat(resolver.resolve("common.yaml#/components/schemas/Pet", document)).isNull();
    }

    @Test
    void returnsNullForACyclicReference() {
        Schema<?> first = new Schema<>().$ref("#/components/schemas/Second");
        Schema<?> second = new Schema<>().$ref("#/components/schemas/First");
        OpenAPI document = document(
                new Components().addSchemas("First", first).addSchemas("Second", second));

        assertThat(resolver.resolve("#/components/schemas/First", document)).isNull();
    }

    @Test
    void returnsNullWithoutAReferenceOrADocument() {
        assertThat(resolver.resolve("#/components/schemas/Pet", null)).isNull();
        assertThat(resolver.resolve(null, new OpenAPI())).isNull();
    }

    private static OpenAPI document(Components components) {
        return new OpenAPI().components(components);
    }
}
