package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.DetectedChange;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OperationComparator {

    public List<DetectedChange> compare(
            String path,
            PathItem oldPath,
            PathItem newPath) {

        List<DetectedChange> changes = new ArrayList<>();

        Map<HttpMethod, Operation> oldOperations =
                oldPath.readOperationsMap();

        Map<HttpMethod, Operation> newOperations =
                newPath.readOperationsMap();

        for (Map.Entry<HttpMethod, Operation> entry :
                oldOperations.entrySet()) {

            HttpMethod method = entry.getKey();

            Operation newOperation = newOperations.get(method);

            if (newOperation == null) {
                changes.add(new DetectedChange(
                        ChangeType.OPERATION_REMOVED,
                        new ChangeLocation(
                                path,
                                method.name(),
                                null
                        ),
                        "Operation " + method.name()
                                + " " + path + " was removed."
                ));

                continue;
            }

            // Operation oldOperation = entry.getValue();

            // Compare parameters
            // Compare request body
            // Compare responses
        }

        return changes;
    }
}