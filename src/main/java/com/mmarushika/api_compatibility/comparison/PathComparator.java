package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeLocation;
import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.DetectedChange;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PathComparator {

    private final OperationComparator operationComparator;

    public PathComparator(OperationComparator operationComparator) {
        this.operationComparator = operationComparator;
    }

    public List<DetectedChange> compare(
            Map<String, PathItem> oldPaths,
            Map<String, PathItem> newPaths) {

        List<DetectedChange> changes = new ArrayList<>();

        if (oldPaths == null) {
            oldPaths = Map.of();
        }

        if (newPaths == null) {
            newPaths = Map.of();
        }

        for (Map.Entry<String, PathItem> entry : oldPaths.entrySet()) {

            String path = entry.getKey();
            PathItem oldPath = entry.getValue();

            PathItem newPath = newPaths.get(path);

            if (newPath == null) {
                changes.add(new DetectedChange(
                        ChangeType.PATH_REMOVED,
                        new ChangeLocation(path, null, null),
                        "Path '" + path + "' was removed."
                ));

                continue;
            }

            changes.addAll(
                    operationComparator.compare(
                            path,
                            oldPath,
                            newPath
                    )
            );
        }

        return changes;
    }

    public static void main(String[] args) {
        OperationComparator operatorComparator = new OperationComparator();
        PathComparator pathComparator = new PathComparator(operatorComparator);
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        SwaggerParseResult result1 =  parser.readLocation("src/main/java/com/mmarushika/api_compatibility/sample/sample_spec.json", null, null);
        OpenAPI spec1 = result1.getOpenAPI();
        OpenAPI spec2 = parser.readLocation("src/main/java/com/mmarushika/api_compatibility/sample/sample_spec2.json", null, null).getOpenAPI();
        Paths oldPaths = spec1.getPaths();
        Paths newPaths = spec2.getPaths();
        List<DetectedChange> changes = pathComparator.compare(oldPaths, newPaths);
        for(DetectedChange change : changes) {
            System.out.println(change.toString());
        }
    }
}