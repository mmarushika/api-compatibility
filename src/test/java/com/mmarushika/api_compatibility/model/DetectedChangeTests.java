package com.mmarushika.api_compatibility.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DetectedChangeTests {

    @Test
    void carriesTypeImpactLocationAndMessageSeparately() {
        ChangeLocation location = ChangeLocation.forPath("/legacy");

        DetectedChange change = new DetectedChange(
                ChangeType.PATH_REMOVED,
                CompatibilityImpact.BREAKING,
                location,
                "Path '/legacy' was removed.");

        assertThat(change.type()).isEqualTo(ChangeType.PATH_REMOVED);
        assertThat(change.impact()).isEqualTo(CompatibilityImpact.BREAKING);
        assertThat(change.location()).isEqualTo(location);
        assertThat(change.message()).isEqualTo("Path '/legacy' was removed.");
    }

    @Test
    void rendersTheLocationFromTheStructuredLocationNotFromTheMessage() {
        DetectedChange change = new DetectedChange(
                ChangeType.OPERATION_REMOVED,
                CompatibilityImpact.BREAKING,
                ChangeLocation.forOperation("/users", "POST"),
                "Operation POST /users was removed.");

        assertThat(change.toString())
                .isEqualTo("OPERATION_REMOVED [BREAKING] POST /users - Operation POST /users was removed.");
    }

    @Test
    void rejectsIncompleteChanges() {
        ChangeLocation location = ChangeLocation.forPath("/users");

        assertThatThrownBy(() -> new DetectedChange(
                null, CompatibilityImpact.BREAKING, location, "message"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");

        assertThatThrownBy(() -> new DetectedChange(
                ChangeType.PATH_ADDED, null, location, "message"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("impact");

        assertThatThrownBy(() -> new DetectedChange(
                ChangeType.PATH_ADDED, CompatibilityImpact.NON_BREAKING, null, "message"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("location");

        assertThatThrownBy(() -> new DetectedChange(
                ChangeType.PATH_ADDED, CompatibilityImpact.NON_BREAKING, location, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("message");
    }
}
