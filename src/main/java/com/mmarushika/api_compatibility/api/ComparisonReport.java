package com.mmarushika.api_compatibility.api;

import com.mmarushika.api_compatibility.model.CompatibilityImpact;
import com.mmarushika.api_compatibility.model.DetectedChange;

import java.util.List;

public record ComparisonReport(boolean breaking, Summary summary, List<DetectedChange> changes) {

    public static ComparisonReport of(List<DetectedChange> changes) {
        int breaking = 0;
        int nonBreaking = 0;
        int unknown = 0;

        for (DetectedChange change : changes) {
            if (change.impact() == CompatibilityImpact.BREAKING) {
                breaking++;
            } else if (change.impact() == CompatibilityImpact.NON_BREAKING) {
                nonBreaking++;
            } else {
                unknown++;
            }
        }

        return new ComparisonReport(
                breaking > 0,
                new Summary(changes.size(), breaking, nonBreaking, unknown),
                List.copyOf(changes));
    }

    public record Summary(int total, int breaking, int nonBreaking, int unknown) {
    }
}
