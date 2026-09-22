package com.mmarushika.api_compatibility.service;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.Operation;

import java.util.Map;

public class OpenApiParserService {
    public static void main(String[] args) {
        SwaggerParseResult result = new OpenAPIParser().readLocation("https://petstore3.swagger.io/api/v3/openapi.json",
                null, null);

        // or from a file
        // SwaggerParseResult result = new
        // OpenAPIParser().readLocation("./path/to/openapi.yaml", null, null);

        // the parsed POJO
        OpenAPI openAPI = result.getOpenAPI();
        Paths paths = openAPI.getPaths();

        for (Map.Entry<String, PathItem> entry : paths.entrySet()) {
            String path = entry.getKey();
            System.out.println(path);
            PathItem pathItem = entry.getValue();
            Map<HttpMethod, Operation> operationsMap = pathItem.readOperationsMap();
            for(Map.Entry<HttpMethod, Operation> operationEntry : operationsMap.entrySet()) {
                System.out.println(operationEntry.getKey());
            }
        }
        for(HttpMethod method : HttpMethod.values()) {
            System.out.println(method);
        }
        // System.out.println(PathItem.HttpMethod.POST);
        // System.out.println(openAPI.getPaths());

        // if (result.getMessages() != null)
        //     result.getMessages().forEach(System.err::println); // validation errors and warnings
    }
}
