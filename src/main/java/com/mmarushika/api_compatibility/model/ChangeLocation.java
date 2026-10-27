package com.mmarushika.api_compatibility.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ChangeLocation(
        String path,
        String method,
        ChangeContext context,
        String statusCode,
        String mediaType,
        String parameterName,
        String parameterIn,
        String propertyPath) {

    public ChangeLocation {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(context, "context must not be null");
    }

    public static ChangeLocation forPath(String path) {
        return new ChangeLocation(path, null, ChangeContext.PATH, null, null, null, null, null);
    }

    public static ChangeLocation forOperation(String path, String method) {
        return new ChangeLocation(path, method, ChangeContext.OPERATION, null, null, null, null, null);
    }

    public static ChangeLocation forParameter(
            String path,
            String method,
            String parameterName,
            String parameterIn) {
        return new ChangeLocation(
                path, method, ChangeContext.REQUEST, null, null, parameterName, parameterIn, null);
    }
    public static ChangeLocation forRequestBody(String path, String method) {
        return new ChangeLocation(path, method, ChangeContext.REQUEST, null, null, null, null, null);
    }

    public static ChangeLocation forResponse(String path, String method, String statusCode) {
        return new ChangeLocation(
                path, method, ChangeContext.RESPONSE, statusCode, null, null, null, null);
    }

    public static ChangeLocation forResponseHeader(
            String path,
            String method,
            String statusCode,
            String headerName) {
        return new ChangeLocation(
                path, method, ChangeContext.RESPONSE, statusCode, null, headerName, "header", null);
    }

    public ChangeLocation withMediaType(String mediaType) {
        return new ChangeLocation(
                path, method, context, statusCode, mediaType, parameterName, parameterIn, propertyPath);
    }

    public ChangeLocation withPropertyPath(String propertyPath) {
        return new ChangeLocation(
                path, method, context, statusCode, mediaType, parameterName, parameterIn, propertyPath);
    }

    public String describe() {
        List<String> parts = new ArrayList<>();

        parts.add(method == null ? path : method + " " + path);

        if (context == ChangeContext.REQUEST) {
            parts.add("request");
        } else if (context == ChangeContext.RESPONSE) {
            parts.add("response");
        }

        if (statusCode != null) {
            parts.add(statusCode);
        }
        if (mediaType != null) {
            parts.add(mediaType);
        }
        if (parameterName != null) {
            parts.add(parameterIn == null
                    ? "parameter " + parameterName
                    : "parameter " + parameterName + " (" + parameterIn + ")");
        }
        if (propertyPath != null) {
            parts.add(propertyPath);
        }

        return String.join(" ", parts);
    }
}
