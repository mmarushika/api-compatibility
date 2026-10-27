package com.mmarushika.api_compatibility.resolver;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;

import java.util.Map;

public class ReferenceResolver {

    private static final String LOCAL_REFERENCE_PREFIX = "#/";

    private static final String COMPONENTS = "components";
    private static final String PATHS = "paths";
    private static final String INFO = "info";
    private static final String SERVERS = "servers";
    private static final String SECURITY = "security";
    private static final String TAGS = "tags";
    private static final String EXTERNAL_DOCS = "externalDocs";

    private static final String SCHEMAS = "schemas";
    private static final String RESPONSES = "responses";
    private static final String PARAMETERS = "parameters";
    private static final String EXAMPLES = "examples";
    private static final String REQUEST_BODIES = "requestBodies";
    private static final String HEADERS = "headers";
    private static final String SECURITY_SCHEMES = "securitySchemes";
    private static final String LINKS = "links";
    private static final String CALLBACKS = "callbacks";

    /**
     * Whether the reference points inside the given document. Local references that cannot be
     * resolved are dangling; references outside the document are deferred.
     */
    public boolean isLocalReference(String reference) {
        return reference != null && reference.startsWith(LOCAL_REFERENCE_PREFIX);
    }

    public Object resolve(String reference, OpenAPI openAPI) {
        if (reference == null || openAPI == null) {
            return null;
        }

        if (!reference.startsWith(LOCAL_REFERENCE_PREFIX)) {
            return null;
        }

        return resolveLocalReference(reference, openAPI, new java.util.HashSet<>());
    }

    private Object resolveLocalReference(String reference, OpenAPI openAPI, java.util.Set<String> visited) {
        // Detect circular references
        if (!visited.add(reference)) {
            return null;
        }
        String pointer = reference.substring(LOCAL_REFERENCE_PREFIX.length());
        String[] tokens = pointer.split("/");
        Object current = openAPI;
        for (String token : tokens) {
            current = child(current, decodePointerToken(token));
            if (current == null) {
                return null;
            }
        }
        // Resolve nested $ref for known objects
        if (current instanceof io.swagger.v3.oas.models.media.Schema<?> schema) {
            String nested = schema.get$ref();
            if (nested != null) {
                return resolveLocalReference(nested, openAPI, visited);
            }
        } else if (current instanceof io.swagger.v3.oas.models.parameters.Parameter param) {
            String nested = param.get$ref();
            if (nested != null) {
                return resolveLocalReference(nested, openAPI, visited);
            }
        } else if (current instanceof io.swagger.v3.oas.models.parameters.RequestBody body) {
            String nested = body.get$ref();
            if (nested != null) {
                return resolveLocalReference(nested, openAPI, visited);
            }
        } else if (current instanceof io.swagger.v3.oas.models.responses.ApiResponse resp) {
            String nested = resp.get$ref();
            if (nested != null) {
                return resolveLocalReference(nested, openAPI, visited);
            }
        } else if (current instanceof io.swagger.v3.oas.models.headers.Header header) {
            String nested = header.get$ref();
            if (nested != null) {
                return resolveLocalReference(nested, openAPI, visited);
            }
        } else if (current instanceof io.swagger.v3.oas.models.security.SecurityScheme scheme) {
            String nested = scheme.get$ref();
            if (nested != null) {
                return resolveLocalReference(nested, openAPI, visited);
            }
        }
        return current;
    }

    private Object child(Object current, String token) {

        if (current instanceof OpenAPI openAPI) {
            return switch (token) {
                case COMPONENTS -> openAPI.getComponents();
                case PATHS -> openAPI.getPaths();
                case INFO -> openAPI.getInfo();
                case SERVERS -> openAPI.getServers();
                case SECURITY -> openAPI.getSecurity();
                case TAGS -> openAPI.getTags();
                case EXTERNAL_DOCS -> openAPI.getExternalDocs();
                default -> null;
            };
        }

        if (current instanceof Components components) {
            return switch (token) {
                case SCHEMAS -> components.getSchemas();
                case RESPONSES -> components.getResponses();
                case PARAMETERS -> components.getParameters();
                case EXAMPLES -> components.getExamples();
                case REQUEST_BODIES -> components.getRequestBodies();
                case HEADERS -> components.getHeaders();
                case SECURITY_SCHEMES -> components.getSecuritySchemes();
                case LINKS -> components.getLinks();
                case CALLBACKS -> components.getCallbacks();
                default -> null;
            };
        }

        if (current instanceof io.swagger.v3.oas.models.PathItem pathItem) {
            return switch (token.toLowerCase()) {
                case "get" -> pathItem.getGet();
                case "put" -> pathItem.getPut();
                case "post" -> pathItem.getPost();
                case "delete" -> pathItem.getDelete();
                case "options" -> pathItem.getOptions();
                case "head" -> pathItem.getHead();
                case "patch" -> pathItem.getPatch();
                case "trace" -> pathItem.getTrace();
                case PARAMETERS -> pathItem.getParameters();
                case "summary" -> pathItem.getSummary();
                case "description" -> pathItem.getDescription();
                default -> null;
            };
        }

        if (current instanceof io.swagger.v3.oas.models.Operation operation) {
            return switch (token) {
                case PARAMETERS -> operation.getParameters();
                case REQUEST_BODIES, "requestBody" -> operation.getRequestBody();
                case RESPONSES -> operation.getResponses();
                case SECURITY -> operation.getSecurity();
                default -> null;
            };
        }

        if (current instanceof io.swagger.v3.oas.models.responses.ApiResponse response) {
            return switch (token) {
                case HEADERS -> response.getHeaders();
                case "content" -> response.getContent();
                case "description" -> response.getDescription();
                default -> null;
            };
        }

        if (current instanceof io.swagger.v3.oas.models.media.MediaType mediaType) {
            return switch (token) {
                case "schema" -> mediaType.getSchema();
                case EXAMPLES -> mediaType.getExamples();
                case "example" -> mediaType.getExample();
                default -> null;
            };
        }

        if (current instanceof io.swagger.v3.oas.models.headers.Header header) {
            return switch (token) {
                case "schema" -> header.getSchema();
                case "content" -> header.getContent();
                default -> null;
            };
        }

        if (current instanceof io.swagger.v3.oas.models.media.Schema<?> schema) {
            return switch (token) {
                case "properties" -> schema.getProperties();
                case "items" -> schema.getItems();
                case "allOf" -> schema.getAllOf();
                case "oneOf" -> schema.getOneOf();
                case "anyOf" -> schema.getAnyOf();
                case "not" -> schema.getNot();
                case "additionalProperties" -> schema.getAdditionalProperties();
                default -> null;
            };
        }

        if (current instanceof io.swagger.v3.oas.models.parameters.RequestBody requestBody) {
            return switch (token) {
                case "content" -> requestBody.getContent();
                case "description" -> requestBody.getDescription();
                default -> null;
            };
        }

        if (current instanceof java.util.List<?> list) {
            try {
                int index = Integer.parseInt(token);
                if (index >= 0 && index < list.size()) {
                    return list.get(index);
                }
            } catch (NumberFormatException ignored) {
                // Not a numeric index
            }
            return null;
        }

        if (current instanceof Map<?, ?> map) {
            return map.get(token);
        }

        return null;
    }

    private String decodePointerToken(String token) {
        return token
                .replace("~1", "/")
                .replace("~0", "~");
    }
}