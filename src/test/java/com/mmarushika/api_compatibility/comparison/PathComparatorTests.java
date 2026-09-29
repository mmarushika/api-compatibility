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
import io.swagger.v3.oas.models.Paths;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PathComparatorTests {

    @Autowired
    private PathComparator pathComparator;

    @Test
    void reportsNoChangesWhenBothDocumentsDeclareTheSamePaths() {
        Paths oldPaths = paths("/users", getAndPost());

        Paths newPaths = paths("/users", getAndPost());

        assertThat(pathComparator.compare(context(oldPaths, newPaths))).isEmpty();
    }

    @Test
    void reportsRemovedPathWhenTheNewDocumentDropsIt() {
        Paths oldPaths = paths("/users", getAndPost());
        oldPaths.addPathItem("/legacy", get());

        Paths newPaths = paths("/users", getAndPost());

        assertThat(pathComparator.compare(context(oldPaths, newPaths))).containsExactly(
                new DetectedChange(
                        ChangeType.PATH_REMOVED,
                        CompatibilityImpact.BREAKING,
                        ChangeLocation.forPath("/legacy"),
                        "Path '/legacy' was removed."));
    }

    @Test
    void reportsAddedPathWhenTheNewDocumentIntroducesIt() {
        Paths oldPaths = paths("/users", get());

        Paths newPaths = paths("/users", get());
        newPaths.addPathItem("/reports", get());

        assertThat(pathComparator.compare(context(oldPaths, newPaths))).containsExactly(
                new DetectedChange(
                        ChangeType.PATH_ADDED,
                        CompatibilityImpact.NON_BREAKING,
                        ChangeLocation.forPath("/reports"),
                        "Path '/reports' was added."));
    }

    @Test
    void reportsOperationChangesForAPathDeclaredByBothDocuments() {
        Paths oldPaths = paths("/users", getAndPost());

        Paths newPaths = paths("/users", get());

        assertThat(pathComparator.compare(context(oldPaths, newPaths))).containsExactly(
                new DetectedChange(
                        ChangeType.OPERATION_REMOVED,
                        CompatibilityImpact.BREAKING,
                        ChangeLocation.forOperation("/users", "POST"),
                        "Operation POST /users was removed."));
    }

    @Test
    void comparesEveryPathExactlyOnce() {
        Paths oldPaths = paths("/users", getAndPost());
        oldPaths.addPathItem("/orders", get());

        Paths newPaths = paths("/users", get());
        newPaths.addPathItem("/invoices", post());

        assertThat(pathComparator.compare(context(oldPaths, newPaths))).containsExactly(
                new DetectedChange(
                        ChangeType.OPERATION_REMOVED,
                        CompatibilityImpact.BREAKING,
                        ChangeLocation.forOperation("/users", "POST"),
                        "Operation POST /users was removed."),
                new DetectedChange(
                        ChangeType.PATH_REMOVED,
                        CompatibilityImpact.BREAKING,
                        ChangeLocation.forPath("/orders"),
                        "Path '/orders' was removed."),
                new DetectedChange(
                        ChangeType.PATH_ADDED,
                        CompatibilityImpact.NON_BREAKING,
                        ChangeLocation.forPath("/invoices"),
                        "Path '/invoices' was added."));
    }

    @Test
    void reportsAPathWithRenamedPathParameterAsRemovedAndAdded() {
        // Deliberate current behaviour: paths are matched by literal template. Renamed path
        // parameters are a later feature and must not be guessed at yet.
        Paths oldPaths = paths("/users/{id}", get());

        Paths newPaths = paths("/users/{userId}", get());

        assertThat(pathComparator.compare(context(oldPaths, newPaths))).containsExactly(
                new DetectedChange(
                        ChangeType.PATH_REMOVED,
                        CompatibilityImpact.BREAKING,
                        ChangeLocation.forPath("/users/{id}"),
                        "Path '/users/{id}' was removed."),
                new DetectedChange(
                        ChangeType.PATH_ADDED,
                        CompatibilityImpact.NON_BREAKING,
                        ChangeLocation.forPath("/users/{userId}"),
                        "Path '/users/{userId}' was added."));
    }

    @Test
    void treatsMissingPathMapsAsEmptyDocuments() {
        assertThat(pathComparator.compare(context(null, null))).isEmpty();

        assertThat(pathComparator.compare(context(null, paths("/users", get()))))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.PATH_ADDED);

        assertThat(pathComparator.compare(context(paths("/users", get()), null)))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.PATH_REMOVED);
    }

    private static ComparisonContext context(Paths oldPaths, Paths newPaths) {
        return new ComparisonContext(
                new OpenAPI().paths(oldPaths),
                new OpenAPI().paths(newPaths),
                new ReferenceResolver());
    }

    private static Paths paths(String path, PathItem pathItem) {
        return new Paths().addPathItem(path, pathItem);
    }

    private static PathItem get() {
        return new PathItem().get(new Operation());
    }

    private static PathItem post() {
        return new PathItem().post(new Operation());
    }

    private static PathItem getAndPost() {
        return get().post(new Operation());
    }
}
