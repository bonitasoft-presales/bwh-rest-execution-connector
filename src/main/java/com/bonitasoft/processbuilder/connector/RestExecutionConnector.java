package com.bonitasoft.processbuilder.connector;

import com.bonitasoft.processbuilder.execution.ConnectorExecutionEngine;
import com.bonitasoft.processbuilder.execution.ConnectorRequest;
import com.bonitasoft.processbuilder.execution.ConnectorResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * Multi-connector for executing REST API calls with built-in authentication management.
 * <p>
 * Supports two usage modes:
 * <ul>
 *   <li><b>Wizard mode</b>: User configures auth, method, URL, and params through individual fields (Pages 1-3)</li>
 *   <li><b>Config mode</b>: A PBConfiguration JSON is provided via {@code configJson}, overriding all individual fields</li>
 * </ul>
 * <p>
 * Authentication types: NONE, BASIC, BEARER, API_KEY, OAUTH2_CLIENT_CREDENTIALS, OAUTH2_PASSWORD.
 * OAuth2 token acquisition and caching is managed internally by {@link ConnectorExecutionEngine}.
 * </p>
 * <p>
 * Lifecycle: VALIDATE → CONNECT → EXECUTE → DISCONNECT
 * </p>
 */
public class RestExecutionConnector extends AbstractConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestExecutionConnector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Page 1: Authentication
    public static final String INPUT_AUTH_TYPE = "authType";
    public static final String INPUT_USERNAME = "username";
    public static final String INPUT_PASSWORD = "password";
    public static final String INPUT_TOKEN = "token";
    public static final String INPUT_API_KEY_NAME = "apiKeyName";
    public static final String INPUT_API_KEY_VALUE = "apiKeyValue";
    public static final String INPUT_API_KEY_LOCATION = "apiKeyLocation";
    public static final String INPUT_TOKEN_URL = "tokenUrl";
    public static final String INPUT_CLIENT_ID = "clientId";
    public static final String INPUT_CLIENT_SECRET = "clientSecret";
    public static final String INPUT_SCOPE = "scope";

    // Page 2: Request Configuration
    public static final String INPUT_HTTP_METHOD = "httpMethod";
    public static final String INPUT_URL = "url";
    public static final String INPUT_PATH = "path";
    public static final String INPUT_CONTENT_TYPE = "contentType";

    // Page 3: Parameters & Options
    public static final String INPUT_BODY = "body";
    public static final String INPUT_HEADERS_JSON = "headersJson";
    public static final String INPUT_QUERY_PARAMS_JSON = "queryParamsJson";
    public static final String INPUT_TEMPLATE_PARAMS_JSON = "templateParamsJson";
    public static final String INPUT_TIMEOUT_MS = "timeoutMs";
    public static final String INPUT_VERIFY_SSL = "verifySsl";
    public static final String INPUT_CONFIG_JSON = "configJson";

    // Outputs
    public static final String OUTPUT_SUCCESS = "success";
    public static final String OUTPUT_ERROR_MESSAGE = "errorMessage";
    public static final String OUTPUT_STATUS_CODE = "statusCode";
    public static final String OUTPUT_RESPONSE_BODY = "responseBody";
    public static final String OUTPUT_RESPONSE_HEADERS = "responseHeaders";
    public static final String OUTPUT_EXECUTION_TIME_MS = "executionTimeMs";
    public static final String OUTPUT_REQUEST_URL = "requestUrl";

    private ConnectorExecutionEngine engine;

    // ========================================================================
    // Phase 1: VALIDATE
    // ========================================================================

    @Override
    public void validateInputParameters() throws ConnectorValidationException {
        String configJson = getStringInput(INPUT_CONFIG_JSON);

        if (isConfigMode(configJson)) {
            validateConfigJson(configJson);
        } else {
            validateWizardInputs();
        }

        Integer timeoutMs = getIntegerInput(INPUT_TIMEOUT_MS);
        if (timeoutMs != null && timeoutMs < 0) {
            throw new ConnectorValidationException("Input '" + INPUT_TIMEOUT_MS + "' must be non-negative");
        }

        LOGGER.info("Validation passed. mode={}", isConfigMode(configJson) ? "config" : "wizard");
    }

    private void validateConfigJson(String configJson) throws ConnectorValidationException {
        try {
            MAPPER.readTree(configJson);
        } catch (Exception e) {
            throw new ConnectorValidationException("Input '" + INPUT_CONFIG_JSON + "' is not valid JSON: " + e.getMessage());
        }
    }

    private void validateWizardInputs() throws ConnectorValidationException {
        String url = getStringInput(INPUT_URL);
        if (url == null || url.isBlank()) {
            throw new ConnectorValidationException("Input '" + INPUT_URL + "' is required when configJson is not provided");
        }

        String httpMethod = getStringInput(INPUT_HTTP_METHOD);
        if (httpMethod == null || httpMethod.isBlank()) {
            throw new ConnectorValidationException("Input '" + INPUT_HTTP_METHOD + "' is required");
        }
    }

    // ========================================================================
    // Phase 2: CONNECT
    // ========================================================================

    @Override
    public void connect() throws ConnectorException {
        engine = new ConnectorExecutionEngine();
        LOGGER.debug("ConnectorExecutionEngine initialized");
    }

    // ========================================================================
    // Phase 3: EXECUTE
    // ========================================================================

    @Override
    protected void executeBusinessLogic() throws ConnectorException {
        try {
            ConnectorRequest request = buildRequest();

            LOGGER.info("Executing REST connector: method={}, url={}",
                    getStringInput(INPUT_HTTP_METHOD), getStringInput(INPUT_URL));

            ConnectorResponse response = engine.execute(request);

            setOutputParameter(OUTPUT_SUCCESS, response.success());
            setOutputParameter(OUTPUT_ERROR_MESSAGE, response.errorMessage());
            setOutputParameter(OUTPUT_STATUS_CODE, response.statusCode());
            setOutputParameter(OUTPUT_RESPONSE_BODY, response.responseBody());
            setOutputParameter(OUTPUT_EXECUTION_TIME_MS, response.executionTimeMs());
            setOutputParameter(OUTPUT_REQUEST_URL, response.requestUrl());

            try {
                setOutputParameter(OUTPUT_RESPONSE_HEADERS, MAPPER.writeValueAsString(response.responseHeaders()));
            } catch (Exception e) {
                setOutputParameter(OUTPUT_RESPONSE_HEADERS, "{}");
            }

            if (response.success()) {
                LOGGER.info("REST connector succeeded: HTTP {}", response.statusCode());
            } else {
                LOGGER.warn("REST connector failed: {} (HTTP {})", response.errorMessage(), response.statusCode());
            }

        } catch (Exception e) {
            LOGGER.error("REST connector execution error: {}", e.getMessage(), e);
            setOutputParameter(OUTPUT_SUCCESS, false);
            setOutputParameter(OUTPUT_ERROR_MESSAGE, e.getMessage());
            setOutputParameter(OUTPUT_STATUS_CODE, -1);
        }
    }

    // ========================================================================
    // Phase 4: DISCONNECT
    // ========================================================================

    @Override
    public void disconnect() throws ConnectorException {
        engine = null;
        LOGGER.debug("ConnectorExecutionEngine released");
    }

    // ========================================================================
    // Request Building
    // ========================================================================

    private ConnectorRequest buildRequest() throws Exception {
        String configJson = getStringInput(INPUT_CONFIG_JSON);

        if (isConfigMode(configJson)) {
            return buildFromConfigJson(configJson);
        }
        return buildFromWizardInputs();
    }

    /**
     * Config mode: delegate directly to engine with PBConfiguration JSON.
     */
    private ConnectorRequest buildFromConfigJson(String configJson) throws Exception {
        Map<String, String> templateParams = parseJsonMap(getStringInput(INPUT_TEMPLATE_PARAMS_JSON));
        Map<String, String> headers = parseJsonMap(getStringInput(INPUT_HEADERS_JSON));

        // Extract methodName from configJson if present
        String methodName = null;
        var configNode = MAPPER.readTree(configJson);
        if (configNode.has("methodName")) {
            methodName = configNode.get("methodName").asText();
        }

        return ConnectorRequest.builder(configJson)
                .methodName(methodName)
                .params(templateParams)
                .body(getStringInput(INPUT_BODY) != null ? getStringInput(INPUT_BODY) : "")
                .headers(headers)
                .timeoutMs(getIntegerInput(INPUT_TIMEOUT_MS) != null ? getIntegerInput(INPUT_TIMEOUT_MS) : 0)
                .verifySsl(getBooleanInput(INPUT_VERIFY_SSL))
                .build();
    }

    /**
     * Wizard mode: build PBConfiguration JSON from individual fields.
     */
    private ConnectorRequest buildFromWizardInputs() throws Exception {
        String generatedConfig = buildConfigJsonFromWizard();
        Map<String, String> templateParams = parseJsonMap(getStringInput(INPUT_TEMPLATE_PARAMS_JSON));
        Map<String, String> headers = parseJsonMap(getStringInput(INPUT_HEADERS_JSON));

        return ConnectorRequest.builder(generatedConfig)
                .params(templateParams)
                .body(getStringInput(INPUT_BODY) != null ? getStringInput(INPUT_BODY) : "")
                .headers(headers)
                .timeoutMs(getIntegerInput(INPUT_TIMEOUT_MS) != null ? getIntegerInput(INPUT_TIMEOUT_MS) : 0)
                .verifySsl(getBooleanInput(INPUT_VERIFY_SSL))
                .methodOverride(getStringInput(INPUT_HTTP_METHOD))
                .queryParams(parseJsonMap(getStringInput(INPUT_QUERY_PARAMS_JSON)))
                .build();
    }

    /**
     * Builds a PBConfiguration-compatible JSON from wizard page fields.
     * This allows the ConnectorExecutionEngine to process it uniformly.
     */
    String buildConfigJsonFromWizard() throws Exception {
        ObjectNode root = MAPPER.createObjectNode();

        // Endpoint
        String url = getStringInput(INPUT_URL);
        String path = getStringInput(INPUT_PATH);
        root.put("baseUrl", url != null ? url : "");

        // Single method entry built from wizard fields
        ObjectNode method = MAPPER.createObjectNode();
        method.put("name", "default");
        method.put("httpMethod", getStringInput(INPUT_HTTP_METHOD) != null ? getStringInput(INPUT_HTTP_METHOD) : "GET");
        method.put("path", path != null ? path : "");

        String contentType = getStringInput(INPUT_CONTENT_TYPE);
        if (contentType != null && !contentType.isBlank()) {
            ObjectNode methodHeaders = MAPPER.createObjectNode();
            methodHeaders.put("Content-Type", contentType);
            method.set("headers", methodHeaders);
        }

        // Query params
        String queryParamsJson = getStringInput(INPUT_QUERY_PARAMS_JSON);
        if (queryParamsJson != null && !queryParamsJson.isBlank()) {
            method.set("queryParams", MAPPER.readTree(queryParamsJson));
        }

        root.set("methods", MAPPER.createArrayNode().add(method));

        // Auth configuration
        ObjectNode auth = buildAuthNode();
        root.set("auth", auth);

        // Timeout & SSL
        Integer timeoutMs = getIntegerInput(INPUT_TIMEOUT_MS);
        root.put("timeoutMs", timeoutMs != null ? timeoutMs : 30000);
        Boolean verifySsl = getBooleanInput(INPUT_VERIFY_SSL);
        root.put("verifySsl", verifySsl != null ? verifySsl : true);

        return MAPPER.writeValueAsString(root);
    }

    private ObjectNode buildAuthNode() {
        ObjectNode auth = MAPPER.createObjectNode();
        String authType = getStringInput(INPUT_AUTH_TYPE);
        auth.put("authType", authType != null ? authType : "NONE");

        if (authType == null) return auth;

        switch (authType) {
            case "BASIC" -> {
                putIfNotNull(auth, "username", getStringInput(INPUT_USERNAME));
                putIfNotNull(auth, "password", getStringInput(INPUT_PASSWORD));
            }
            case "BEARER" -> putIfNotNull(auth, "token", getStringInput(INPUT_TOKEN));
            case "API_KEY" -> {
                putIfNotNull(auth, "keyName", getStringInput(INPUT_API_KEY_NAME));
                putIfNotNull(auth, "keyValue", getStringInput(INPUT_API_KEY_VALUE));
                putIfNotNull(auth, "location", getStringInput(INPUT_API_KEY_LOCATION));
            }
            case "OAUTH2_CLIENT_CREDENTIALS", "OAUTH2_PASSWORD" -> {
                putIfNotNull(auth, "tokenUrl", getStringInput(INPUT_TOKEN_URL));
                putIfNotNull(auth, "clientId", getStringInput(INPUT_CLIENT_ID));
                putIfNotNull(auth, "clientSecret", getStringInput(INPUT_CLIENT_SECRET));
                putIfNotNull(auth, "scope", getStringInput(INPUT_SCOPE));
                if ("OAUTH2_PASSWORD".equals(authType)) {
                    putIfNotNull(auth, "username", getStringInput(INPUT_USERNAME));
                    putIfNotNull(auth, "password", getStringInput(INPUT_PASSWORD));
                }
            }
            default -> { /* NONE - no additional fields */ }
        }

        return auth;
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private boolean isConfigMode(String configJson) {
        return configJson != null && !configJson.isBlank();
    }

    private void putIfNotNull(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private Map<String, String> parseJsonMap(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            LOGGER.warn("Failed to parse JSON map: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    String getStringInput(String name) {
        Object value = getInputParameter(name);
        return value != null ? value.toString() : null;
    }

    Integer getIntegerInput(String name) {
        Object value = getInputParameter(name);
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    Boolean getBooleanInput(String name) {
        Object value = getInputParameter(name);
        if (value == null) return null;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Allows injection of a custom engine for testing.
     */
    void setEngine(ConnectorExecutionEngine engine) {
        this.engine = engine;
    }
}
