package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.ComparisonContext;
import com.mmarushika.api_compatibility.model.DetectedChange;
import com.mmarushika.api_compatibility.resolver.ReferenceResolver;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
class OperationComparatorTests {

    @Autowired
    private OperationComparator operationComparator;

    @Test
    void reportsNoChangesForAnUnchangedOperation() {
        PathItem getAndPost = new PathItem().get(new Operation()).post(new Operation());

        assertThat(operationComparator.compare(context(), "/users", getAndPost, getAndPost)).isEmpty();
    }

    @Test
    void reportsRemovedOperationWhenTheNewDocumentDropsTheMethod() {
        PathItem oldPathItem = new PathItem().get(new Operation()).post(new Operation());
        PathItem newPathItem = new PathItem().get(new Operation());

        assertThat(operationComparator.compare(context(), "/users", oldPathItem, newPathItem)).containsExactly(
                new DetectedChange(
                        ChangeType.OPERATION_REMOVED,
                        CompatibilityImpact.BREAKING,
                        ChangeLocation.forOperation("/users", "POST"),
                        "Operation POST /users was removed."));
    }

    @Test
    void reportsAddedOperationWhenTheNewDocumentIntroducesTheMethod() {
        PathItem oldPathItem = new PathItem().get(new Operation());
        PathItem newPathItem = new PathItem().get(new Operation()).post(new Operation());

        assertThat(operationComparator.compare(context(), "/users", oldPathItem, newPathItem)).containsExactly(
                new DetectedChange(
                        ChangeType.OPERATION_ADDED,
                        CompatibilityImpact.NON_BREAKING,
                        ChangeLocation.forOperation("/users", "POST"),
                        "Operation POST /users was added."));
    }

    @Test
    void reportsOnlyTheMethodThatActuallyChanged() {
        PathItem oldPathItem = new PathItem()
                .get(new Operation())
                .post(new Operation())
                .delete(new Operation());
        PathItem newPathItem = new PathItem()
                .get(new Operation())
                .delete(new Operation());

        assertThat(operationComparator.compare(context(), "/users", oldPathItem, newPathItem))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.OPERATION_REMOVED);

        List<DetectedChange> changes = operationComparator.compare(context(), "/users", oldPathItem, newPathItem);

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).location().method()).isEqualTo("POST");
    }

    @Test
    void reportsNothingWhenBothPathItemsDeclareNoOperations() {
        assertThat(operationComparator.compare(context(), "/users", new PathItem(), new PathItem())).isEmpty();
    }

    @Test
    void treatsMissingPathItemsAsEmpty() {
        PathItem get = new PathItem().get(new Operation());

        assertThat(operationComparator.compare(context(), "/users", null, null)).isEmpty();

        assertThat(operationComparator.compare(context(), "/users", null, get))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.OPERATION_ADDED);

        assertThat(operationComparator.compare(context(), "/users", get, null))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.OPERATION_REMOVED);
    }

    @Test
    void reportsAddingSecurityToPublicOperationAsBreaking() {
        PathItem publicItem = new PathItem().get(new Operation());
        PathItem securedItem = new PathItem().get(
                new Operation().addSecurityItem(
                        new io.swagger.v3.oas.models.security.SecurityRequirement().addList("BearerAuth")));

        assertThat(operationComparator.compare(context(), "/users", publicItem, securedItem))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .contains(tuple(ChangeType.OPERATION_REQUIREDNESS_ADDED, CompatibilityImpact.BREAKING));
    }

    @Test
    void recordsTheHttpMethodInUppercaseInTheLocation() {
        PathItem get = new PathItem().get(new Operation());

        DetectedChange change = operationComparator.compare(context(), "/users", get, new PathItem()).get(0);

        assertThat(change.location().method()).isEqualTo("GET");
        assertThat(change.location().context()).isEqualTo(ChangeLocation.forOperation("/users", "GET").context());
        assertThat(change.toString()).startsWith("OPERATION_REMOVED [BREAKING] GET /users");
    }

    @Test
    void reportsRequestMediaTypeChangesOfASharedMethod() {
        Operation oldOperation = new Operation().requestBody(new RequestBody()
                .content(new Content().addMediaType("application/json", new MediaType())));
        Operation newOperation = new Operation().requestBody(new RequestBody()
                .content(new Content().addMediaType("application/xml", new MediaType())));

        assertThat(operationComparator.compare(
                context(), "/users", new PathItem().post(oldOperation), new PathItem().post(newOperation)))
                .extracting(DetectedChange::type)
                .containsExactly(
                        ChangeType.REQUEST_MEDIA_TYPE_REMOVED,
                        ChangeType.REQUEST_MEDIA_TYPE_ADDED);
    }

    @Test
    void reportsSchemaChangesInsideTheResponsesOfASharedMethod() {
        Operation oldOperation = new Operation().responses(new ApiResponses().addApiResponse(
                "200",
                new ApiResponse().content(new Content().addMediaType(
                        "application/json", new MediaType().schema(new IntegerSchema())))));
        Operation newOperation = new Operation().responses(new ApiResponses().addApiResponse(
                "200",
                new ApiResponse().content(new Content().addMediaType(
                        "application/json", new MediaType().schema(new StringSchema())))));

        assertThat(operationComparator.compare(
                context(), "/users", new PathItem().get(oldOperation), new PathItem().get(newOperation)))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.SCHEMA_TYPE_CHANGED);
    }

    private static ComparisonContext context() {
        return new ComparisonContext(new OpenAPI(), new OpenAPI(), new ReferenceResolver());
    }
}
