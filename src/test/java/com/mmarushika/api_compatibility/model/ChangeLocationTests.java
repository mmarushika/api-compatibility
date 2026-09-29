package com.mmarushika.api_compatibility.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeLocationTests {

    @Test
    void pathLevelLocationCarriesOnlyThePath() {
        ChangeLocation location = ChangeLocation.forPath("/users");

        assertThat(location.path()).isEqualTo("/users");
        assertThat(location.method()).isNull();
        assertThat(location.context()).isEqualTo(ChangeContext.PATH);
        assertThat(location.describe()).isEqualTo("/users");
    }

    @Test
    void operationLevelLocationCarriesMethodAndPath() {
        ChangeLocation location = ChangeLocation.forOperation("/users", "POST");

        assertThat(location.context()).isEqualTo(ChangeContext.OPERATION);
        assertThat(location.method()).isEqualTo("POST");
        assertThat(location.describe()).isEqualTo("POST /users");
    }

    @Test
    void parameterLevelLocationCarriesTheParameterNameAndRequestDirection() {
        ChangeLocation location = ChangeLocation.forParameter("/users", "GET", "page", "query");

        assertThat(location.context()).isEqualTo(ChangeContext.REQUEST);
        assertThat(location.parameterName()).isEqualTo("page");
        assertThat(location.parameterIn()).isEqualTo("query");
        assertThat(location.describe()).isEqualTo("GET /users request parameter page (query)");
    }

    @Test
    void requestBodyLocationCarriesMediaTypeAndPropertyPath() {
        ChangeLocation location = ChangeLocation.forRequestBody("/users", "POST")
                .withMediaType("application/json")
                .withPropertyPath("email");

        assertThat(location.context()).isEqualTo(ChangeContext.REQUEST);
        assertThat(location.describe()).isEqualTo("POST /users request application/json email");
    }

    @Test
    void responseLocationCarriesStatusCodeMediaTypeAndPropertyPath() {
        ChangeLocation location = ChangeLocation.forResponse("/users", "GET", "200")
                .withMediaType("application/json")
                .withPropertyPath("id");

        assertThat(location.statusCode()).isEqualTo("200");
        assertThat(location.context()).isEqualTo(ChangeContext.RESPONSE);
        assertThat(location.describe()).isEqualTo("GET /users response 200 application/json id");
    }

    @Test
    void addingAMediaTypeLeavesTheOriginalLocationUnchanged() {
        ChangeLocation requestBody = ChangeLocation.forRequestBody("/users", "POST");

        ChangeLocation withMediaType = requestBody.withMediaType("application/json");

        assertThat(requestBody.mediaType()).isNull();
        assertThat(withMediaType.mediaType()).isEqualTo("application/json");
        assertThat(withMediaType).isNotEqualTo(requestBody);
    }

    @Test
    void twoLocationsAreEqualOnlyWhenEveryComponentMatches() {
        assertThat(ChangeLocation.forOperation("/users", "GET"))
                .isEqualTo(ChangeLocation.forOperation("/users", "GET"));

        assertThat(ChangeLocation.forOperation("/users", "GET"))
                .isNotEqualTo(ChangeLocation.forOperation("/users", "POST"));

        assertThat(ChangeLocation.forRequestBody("/users", "POST").withPropertyPath("email"))
                .isNotEqualTo(ChangeLocation.forRequestBody("/users", "POST").withPropertyPath("name"));
    }

    @Test
    void rejectsLocationWithoutPathOrContext() {
        assertThatThrownBy(() -> new ChangeLocation(
                null, "GET", ChangeContext.OPERATION, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("path");

        assertThatThrownBy(() -> new ChangeLocation(
                "/users", "GET", null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context");
    }
}
