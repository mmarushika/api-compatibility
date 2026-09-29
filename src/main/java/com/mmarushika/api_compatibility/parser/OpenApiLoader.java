package com.mmarushika.api_compatibility.parser;

import com.mmarushika.api_compatibility.exception.InvalidOpenApiException;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

public class OpenApiLoader {

    public OpenAPI load(String location) {
        return parsed(
                new OpenAPIV3Parser().readLocation(location, null, parseOptions()),
                location);
    }

    public OpenAPI loadContents(String contents) {
        return parsed(
                new OpenAPIV3Parser().readContents(contents, null, parseOptions()),
                "the given content");
    }

    private static OpenAPI parsed(SwaggerParseResult result, String source) {
        OpenAPI openAPI = result.getOpenAPI();

        if (openAPI == null) {
            throw new InvalidOpenApiException(
                    "Could not read an OpenAPI document from " + source + messagesOf(result));
        }

        return openAPI;
    }

    private static String messagesOf(SwaggerParseResult result) {
        return result.getMessages() == null || result.getMessages().isEmpty()
                ? "."
                : ": " + String.join("; ", result.getMessages());
    }

    private static ParseOptions parseOptions() {
        // Deserialize only. References are resolved explicitly by ReferenceResolver.
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        return options;
    }
}