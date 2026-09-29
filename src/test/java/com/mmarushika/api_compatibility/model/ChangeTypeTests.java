package com.mmarushika.api_compatibility.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeTypeTests {

    /**
     * {@code SUBJECT_EVENT} where the subject is owned by exactly one comparator and the event
     * states the direction of the change.
     */
    private static final Pattern SUBJECT_EVENT = Pattern.compile(
            "^(PATH|OPERATION|PARAMETER|REQUEST_BODY|REQUEST_MEDIA_TYPE|RESPONSE|RESPONSE_MEDIA_TYPE"
                    + "|SCHEMA_PROPERTY|SCHEMA_REFERENCE|SCHEMA)"
                    + "_(ADDED|REMOVED|REQUIREDNESS_ADDED|REQUIREDNESS_REMOVED|TYPE_CHANGED"
                    + "|FORMAT_CHANGED|NULLABLE_CHANGED|ENUM_CHANGED|CONSTRAINT_CHANGED"
                    + "|ADDITIONAL_PROPERTIES_CHANGED|UNRESOLVED)$");

    private static final List<String> NAMES = Arrays.stream(ChangeType.values())
            .map(Enum::name)
            .toList();

    @Test
    void everyChangeTypeFollowsTheSubjectEventConvention() {
        for (ChangeType type : ChangeType.values()) {
            assertThat(type.name())
                    .as("ChangeType %s must be SUBJECT_EVENT with a directional event", type)
                    .matches(SUBJECT_EVENT);
        }
    }

    @Test
    void subjectsThatCanAppearOrDisappearHaveBothDirections() {
        assertThat(NAMES).contains(
                "PATH_ADDED", "PATH_REMOVED",
                "OPERATION_ADDED", "OPERATION_REMOVED",
                "PARAMETER_ADDED", "PARAMETER_REMOVED",
                "REQUEST_BODY_ADDED", "REQUEST_BODY_REMOVED",
                "REQUEST_MEDIA_TYPE_ADDED", "REQUEST_MEDIA_TYPE_REMOVED",
                "RESPONSE_ADDED", "RESPONSE_REMOVED",
                "RESPONSE_MEDIA_TYPE_ADDED", "RESPONSE_MEDIA_TYPE_REMOVED",
                "SCHEMA_ADDED", "SCHEMA_REMOVED",
                "SCHEMA_PROPERTY_ADDED", "SCHEMA_PROPERTY_REMOVED");
    }

    @Test
    void unresolvedReferencesAreReportedDistinctlyFromRemovals() {
        assertThat(NAMES).contains("SCHEMA_REFERENCE_UNRESOLVED");
        assertThat(NAMES).doesNotContain("COMPONENT_REMOVED");
    }

    @Test
    void subjectsThatCanFlipRequirednessHaveBothDirections() {
        assertThat(NAMES).contains(
                "PARAMETER_REQUIREDNESS_ADDED", "PARAMETER_REQUIREDNESS_REMOVED",
                "REQUEST_BODY_REQUIREDNESS_ADDED", "REQUEST_BODY_REQUIREDNESS_REMOVED",
                "SCHEMA_PROPERTY_REQUIREDNESS_ADDED", "SCHEMA_PROPERTY_REQUIREDNESS_REMOVED");
    }

    @Test
    void noChangeTypeUsesAVagueBareChangeEvent() {
        assertThat(NAMES).noneMatch(name -> name.endsWith("_CHANGE"));
        assertThat(NAMES).noneMatch(name -> name.endsWith("_REQUIREDNESS_CHANGED"));
    }
}
