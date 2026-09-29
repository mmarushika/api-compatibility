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
import io.swagger.v3.oas.models.parameters.Parameter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ParameterComparatorTests {

    private final ParameterComparator parameterComparator = new ParameterComparator();

    @Test
    void reportsNoChangesWhenTheSameParametersAreDeclared() {
        assertThat(compare(List.of(query("page", false)), List.of(query("page", false)))).isEmpty();
    }

    @Test
    void reportsAnAddedRequiredParameterAsBreaking() {
        assertThat(compare(List.of(), List.of(query("page", true)))).containsExactly(
                new DetectedChange(
                        ChangeType.PARAMETER_ADDED,
                        CompatibilityImpact.BREAKING,
                        ChangeLocation.forParameter("/users", "GET", "page", "query"),
                        "Parameter query.page of GET /users was added."));
    }

    @Test
    void reportsAnAddedOptionalParameterAsNonBreaking() {
        assertThat(compare(List.of(), List.of(query("page", false))))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(ChangeType.PARAMETER_ADDED, CompatibilityImpact.NON_BREAKING));
    }

    @Test
    void reportsARemovedRequiredParameterAsBreaking() {
        assertThat(compare(List.of(query("page", true)), List.of()))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(ChangeType.PARAMETER_REMOVED, CompatibilityImpact.BREAKING));
    }

    @Test
    void reportsARemovedOptionalParameterAsNonBreaking() {
        assertThat(compare(List.of(query("page", false)), List.of()))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(ChangeType.PARAMETER_REMOVED, CompatibilityImpact.NON_BREAKING));
    }

    @Test
    void reportsRequirednessChangesInBothDirections() {
        assertThat(compare(List.of(query("page", false)), List.of(query("page", true))))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.PARAMETER_REQUIREDNESS_ADDED, CompatibilityImpact.BREAKING));

        assertThat(compare(List.of(query("page", true)), List.of(query("page", false))))
                .extracting(DetectedChange::type, DetectedChange::impact)
                .containsExactly(tuple(
                        ChangeType.PARAMETER_REQUIREDNESS_REMOVED, CompatibilityImpact.NON_BREAKING));
    }

    @Test
    void identifiesParametersByNameAndLocationTogether() {
        // query.page and path.page are two different parameters, so this is a removal plus an add.
        assertThat(compare(List.of(query("page", false)), List.of(path("page"))))
                .extracting(DetectedChange::type, change -> change.location().parameterIn())
                .containsExactly(
                        tuple(ChangeType.PARAMETER_REMOVED, "query"),
                        tuple(ChangeType.PARAMETER_ADDED, "path"));
    }

    @Test
    void treatsMissingParameterListsAsEmpty() {
        assertThat(parameterComparator.compare("/users", "GET", null, null)).isEmpty();

        assertThat(parameterComparator.compare("/users", "GET", null, List.of(query("page", true))))
                .extracting(DetectedChange::type)
                .containsExactly(ChangeType.PARAMETER_ADDED);
    }

    @Test
    void ignoresAParameterWhoseReferenceWasNotResolved() {
        // A reference is not a parameter name. Resolution belongs to the loading layer, so a
        // parameter that still has no name is not compared under its reference.
        Parameter unresolved = new Parameter().$ref("#/components/parameters/PageParam");

        assertThat(compare(List.of(), List.of(unresolved))).isEmpty();
        assertThat(compare(List.of(unresolved), List.of())).isEmpty();
    }

    @Test
    void appliesPathLevelParametersToTheParametersInScope() {
        PathItem pathItem = new PathItem().addParametersItem(query("page", false));
        Operation operation = new Operation().addParametersItem(path("id"));

        List<Parameter> effective =
                ParameterComparator.resolveOldEffectiveParameters(context(), pathItem, operation);

        assertThat(effective).extracting(Parameter::getName).containsExactly("page", "id");
    }

    @Test
    void letsAnOperationLevelParameterOverrideAPathLevelParameter() {
        PathItem pathItem = new PathItem().addParametersItem(query("page", false));
        Operation operation = new Operation().addParametersItem(query("page", true));

        List<Parameter> effective =
                ParameterComparator.resolveOldEffectiveParameters(context(), pathItem, operation);

        assertThat(effective).hasSize(1);
        assertThat(effective.get(0).getName()).isEqualTo("page");
        assertThat(effective.get(0).getRequired()).isTrue();
    }

    @Test
    void hasNoParametersInScopeWithoutAPathItemOrOperation() {
        assertThat(ParameterComparator.resolveOldEffectiveParameters(context(), null, null)).isEmpty();
    }

    private static ComparisonContext context() {
        return new ComparisonContext(new OpenAPI(), new OpenAPI(), new ReferenceResolver());
    }

    private List<DetectedChange> compare(List<Parameter> oldParameters, List<Parameter> newParameters) {
        return parameterComparator.compare("/users", "GET", oldParameters, newParameters);
    }

    private static Parameter query(String name, boolean required) {
        return new Parameter().name(name).in("query").required(required);
    }

    private static Parameter path(String name) {
        return new Parameter().name(name).in("path");
    }
}
