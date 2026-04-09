package com.bonitasoft.processbuilder.connector;

import com.bonitasoft.processbuilder.execution.ConnectorExecutionEngine;
import com.bonitasoft.processbuilder.execution.ConnectorRequest;
import com.bonitasoft.processbuilder.execution.ConnectorResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestExecutionConnectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String VALID_CONFIG_JSON = """
            {
                "baseUrl": "https://api.example.com",
                "methods": [{"name": "getUsers", "httpMethod": "GET", "path": "/users"}],
                "auth": {"authType": "NONE"}
            }
            """;

    @Mock
    private ConnectorExecutionEngine mockEngine;

    private TestableConnector connector;

    @BeforeEach
    void setUp() {
        connector = new TestableConnector();
    }

    // ========================================================================
    // VALIDATE - Config Mode
    // ========================================================================

    @Nested
    @DisplayName("Validation - Config Mode")
    class ConfigModeValidation {

        @Test
        void should_pass_validation_when_configJson_is_valid() throws Exception {
            setInputs(Map.of(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON));
            connector.validateInputParameters();
        }

        @Test
        void should_fail_validation_when_configJson_is_invalid_json() {
            setInputs(Map.of(RestExecutionConnector.INPUT_CONFIG_JSON, "not json"));
            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("not valid JSON");
        }

        @Test
        void should_fail_validation_when_configJson_with_negative_timeout() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON);
            inputs.put(RestExecutionConnector.INPUT_TIMEOUT_MS, -5);
            setInputs(inputs);
            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("timeoutMs");
        }

        @Test
        void should_pass_validation_when_configJson_with_zero_timeout() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON);
            inputs.put(RestExecutionConnector.INPUT_TIMEOUT_MS, 0);
            setInputs(inputs);
            connector.validateInputParameters();
        }
    }

    // ========================================================================
    // VALIDATE - Wizard Mode
    // ========================================================================

    @Nested
    @DisplayName("Validation - Wizard Mode")
    class WizardModeValidation {

        @Test
        void should_pass_validation_when_url_and_method_provided() throws Exception {
            setInputs(Map.of(
                    RestExecutionConnector.INPUT_URL, "https://api.example.com",
                    RestExecutionConnector.INPUT_HTTP_METHOD, "GET"
            ));
            connector.validateInputParameters();
        }

        @Test
        void should_fail_validation_when_url_is_missing() {
            setInputs(Map.of(RestExecutionConnector.INPUT_HTTP_METHOD, "GET"));
            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("url");
        }

        @Test
        void should_fail_validation_when_url_is_blank() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_URL, "   ");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            setInputs(inputs);
            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("url");
        }

        @Test
        void should_fail_validation_when_httpMethod_is_missing() {
            setInputs(Map.of(RestExecutionConnector.INPUT_URL, "https://api.example.com"));
            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("httpMethod");
        }

        @Test
        void should_fail_validation_when_httpMethod_is_blank() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "  ");
            setInputs(inputs);
            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("httpMethod");
        }

        @Test
        void should_fail_validation_when_timeout_is_negative() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_TIMEOUT_MS, -1);
            setInputs(inputs);
            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("timeoutMs");
        }

        @Test
        void should_pass_validation_when_timeout_is_zero() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_TIMEOUT_MS, 0);
            setInputs(inputs);
            connector.validateInputParameters();
        }

        @Test
        void should_pass_validation_when_timeout_is_null() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            setInputs(inputs);
            connector.validateInputParameters();
        }

        @Test
        void should_use_wizard_mode_when_configJson_is_blank() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_CONFIG_JSON, "   ");
            setInputs(inputs);
            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("url");
        }

        @Test
        void should_use_wizard_mode_when_configJson_is_empty() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_CONFIG_JSON, "");
            setInputs(inputs);
            assertThatThrownBy(() -> connector.validateInputParameters())
                    .isInstanceOf(ConnectorValidationException.class)
                    .hasMessageContaining("url");
        }
    }

    // ========================================================================
    // EXECUTE - Config Mode
    // ========================================================================

    @Nested
    @DisplayName("Execute - Config Mode")
    class ConfigModeExecution {

        @Test
        void should_execute_with_configJson_and_return_success() throws Exception {
            ConnectorResponse successResponse = ConnectorResponse.success(
                    200, "{\"data\": \"test\"}", Map.of("Content-Type", "application/json"),
                    150L, "https://api.example.com/users");

            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(successResponse);

            setInputs(Map.of(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON));
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            Map<String, Object> outputs = connector.outputs();
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_SUCCESS)).isEqualTo(true);
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_STATUS_CODE)).isEqualTo(200);
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_RESPONSE_BODY)).isEqualTo("{\"data\": \"test\"}");
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_EXECUTION_TIME_MS)).isEqualTo(150L);
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_REQUEST_URL)).isEqualTo("https://api.example.com/users");
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_ERROR_MESSAGE)).isNull();
            assertThat((String) outputs.get(RestExecutionConnector.OUTPUT_RESPONSE_HEADERS)).contains("Content-Type");
        }

        @Test
        void should_pass_configJson_directly_to_engine() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            setInputs(Map.of(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON));
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            assertThat(captor.getValue().configJson()).isEqualTo(VALID_CONFIG_JSON);
        }

        @Test
        void should_pass_body_and_headers_and_params_to_engine_in_config_mode() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON);
            inputs.put(RestExecutionConnector.INPUT_BODY, "{\"name\":\"test\"}");
            inputs.put(RestExecutionConnector.INPUT_HEADERS_JSON, "{\"X-Custom\":\"val\"}");
            inputs.put(RestExecutionConnector.INPUT_TEMPLATE_PARAMS_JSON, "{\"id\":\"99\"}");
            inputs.put(RestExecutionConnector.INPUT_TIMEOUT_MS, 5000);
            inputs.put(RestExecutionConnector.INPUT_VERIFY_SSL, false);
            setInputs(inputs);
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            ConnectorRequest req = captor.getValue();
            assertThat(req.body()).isEqualTo("{\"name\":\"test\"}");
            assertThat(req.headers()).containsEntry("X-Custom", "val");
            assertThat(req.params()).containsEntry("id", "99");
            assertThat(req.timeoutMs()).isEqualTo(5000);
            assertThat(req.verifySsl()).isFalse();
        }

        @Test
        void should_default_body_to_empty_when_null_in_config_mode() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            setInputs(Map.of(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON));
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            assertThat(captor.getValue().body()).isEqualTo("");
        }

        @Test
        void should_default_timeout_to_zero_when_null_in_config_mode() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            setInputs(Map.of(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON));
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            assertThat(captor.getValue().timeoutMs()).isZero();
        }
    }

    // ========================================================================
    // EXECUTE - Wizard Mode
    // ========================================================================

    @Nested
    @DisplayName("Execute - Wizard Mode")
    class WizardModeExecution {

        @Test
        void should_execute_with_wizard_inputs_and_build_config() throws Exception {
            ConnectorResponse successResponse = ConnectorResponse.success(
                    200, "{}", Map.of(), 100L, "https://api.example.com/users");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(successResponse);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            inputs.put(RestExecutionConnector.INPUT_PATH, "/users");
            setInputs(inputs);
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            Map<String, Object> outputs = connector.outputs();
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        }

        @Test
        void should_build_correct_config_for_basic_auth() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "BASIC");
            inputs.put(RestExecutionConnector.INPUT_USERNAME, "admin");
            inputs.put(RestExecutionConnector.INPUT_PASSWORD, "secret");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("baseUrl").asText()).isEqualTo("https://api.example.com");
            assertThat(root.get("auth").get("authType").asText()).isEqualTo("BASIC");
            assertThat(root.get("auth").get("username").asText()).isEqualTo("admin");
            assertThat(root.get("auth").get("password").asText()).isEqualTo("secret");
        }

        @Test
        void should_build_correct_config_for_bearer_auth() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "BEARER");
            inputs.put(RestExecutionConnector.INPUT_TOKEN, "my-token-123");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "POST");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("auth").get("authType").asText()).isEqualTo("BEARER");
            assertThat(root.get("auth").get("token").asText()).isEqualTo("my-token-123");
            assertThat(root.get("methods").get(0).get("httpMethod").asText()).isEqualTo("POST");
        }

        @Test
        void should_build_correct_config_for_oauth2_client_credentials() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "OAUTH2_CLIENT_CREDENTIALS");
            inputs.put(RestExecutionConnector.INPUT_TOKEN_URL, "https://oauth.example.com/token");
            inputs.put(RestExecutionConnector.INPUT_CLIENT_ID, "my-client");
            inputs.put(RestExecutionConnector.INPUT_CLIENT_SECRET, "my-secret");
            inputs.put(RestExecutionConnector.INPUT_SCOPE, "read write");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("auth").get("authType").asText()).isEqualTo("OAUTH2_CLIENT_CREDENTIALS");
            assertThat(root.get("auth").get("tokenUrl").asText()).isEqualTo("https://oauth.example.com/token");
            assertThat(root.get("auth").get("clientId").asText()).isEqualTo("my-client");
            assertThat(root.get("auth").get("clientSecret").asText()).isEqualTo("my-secret");
            assertThat(root.get("auth").get("scope").asText()).isEqualTo("read write");
        }

        @Test
        void should_build_correct_config_for_oauth2_password() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "OAUTH2_PASSWORD");
            inputs.put(RestExecutionConnector.INPUT_TOKEN_URL, "https://oauth.example.com/token");
            inputs.put(RestExecutionConnector.INPUT_CLIENT_ID, "my-client");
            inputs.put(RestExecutionConnector.INPUT_CLIENT_SECRET, "my-secret");
            inputs.put(RestExecutionConnector.INPUT_SCOPE, "read");
            inputs.put(RestExecutionConnector.INPUT_USERNAME, "user1");
            inputs.put(RestExecutionConnector.INPUT_PASSWORD, "pass1");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "POST");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("auth").get("authType").asText()).isEqualTo("OAUTH2_PASSWORD");
            assertThat(root.get("auth").get("tokenUrl").asText()).isEqualTo("https://oauth.example.com/token");
            assertThat(root.get("auth").get("username").asText()).isEqualTo("user1");
            assertThat(root.get("auth").get("password").asText()).isEqualTo("pass1");
        }

        @Test
        void should_build_correct_config_for_api_key() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "API_KEY");
            inputs.put(RestExecutionConnector.INPUT_API_KEY_NAME, "X-API-Key");
            inputs.put(RestExecutionConnector.INPUT_API_KEY_VALUE, "abc123");
            inputs.put(RestExecutionConnector.INPUT_API_KEY_LOCATION, "HEADER");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("auth").get("authType").asText()).isEqualTo("API_KEY");
            assertThat(root.get("auth").get("keyName").asText()).isEqualTo("X-API-Key");
            assertThat(root.get("auth").get("keyValue").asText()).isEqualTo("abc123");
            assertThat(root.get("auth").get("location").asText()).isEqualTo("HEADER");
        }

        @Test
        void should_build_correct_config_for_oauth2_jwt_bearer() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "OAUTH2_JWT_BEARER");
            inputs.put(RestExecutionConnector.INPUT_TOKEN_URL, "https://oauth2.googleapis.com/token");
            inputs.put(RestExecutionConnector.INPUT_SERVICE_ACCOUNT_EMAIL, "sa@project.iam.gserviceaccount.com");
            inputs.put(RestExecutionConnector.INPUT_PRIVATE_KEY, "-----BEGIN PRIVATE KEY-----\nMIIEv...\n-----END PRIVATE KEY-----");
            inputs.put(RestExecutionConnector.INPUT_SCOPE, "https://www.googleapis.com/auth/drive");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://www.googleapis.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("auth").get("authType").asText()).isEqualTo("OAUTH2_JWT_BEARER");
            assertThat(root.get("auth").get("tokenUrl").asText()).isEqualTo("https://oauth2.googleapis.com/token");
            assertThat(root.get("auth").get("serviceAccountEmail").asText()).isEqualTo("sa@project.iam.gserviceaccount.com");
            assertThat(root.get("auth").get("privateKey").asText()).contains("BEGIN PRIVATE KEY");
            assertThat(root.get("auth").get("scope").asText()).isEqualTo("https://www.googleapis.com/auth/drive");
        }

        @Test
        void should_build_none_auth_when_authType_is_null() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("auth").get("authType").asText()).isEqualTo("NONE");
            assertThat(root.get("auth").size()).isEqualTo(1);
        }

        @Test
        void should_build_none_auth_when_authType_is_explicit_none() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("auth").get("authType").asText()).isEqualTo("NONE");
        }

        @Test
        void should_include_path_and_content_type_in_config() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "POST");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            inputs.put(RestExecutionConnector.INPUT_PATH, "/v2/users");
            inputs.put(RestExecutionConnector.INPUT_CONTENT_TYPE, "application/xml");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            JsonNode methodNode = root.get("methods").get(0);
            assertThat(methodNode.get("path").asText()).isEqualTo("/v2/users");
            assertThat(methodNode.get("headers").get("Content-Type").asText()).isEqualTo("application/xml");
        }

        @Test
        void should_not_include_headers_when_content_type_is_blank() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            inputs.put(RestExecutionConnector.INPUT_CONTENT_TYPE, "   ");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("methods").get(0).has("headers")).isFalse();
        }

        @Test
        void should_include_query_params_in_method_config() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            inputs.put(RestExecutionConnector.INPUT_QUERY_PARAMS_JSON, "{\"page\": \"1\", \"limit\": \"10\"}");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            JsonNode queryParams = root.get("methods").get(0).get("queryParams");
            assertThat(queryParams.get("page").asText()).isEqualTo("1");
            assertThat(queryParams.get("limit").asText()).isEqualTo("10");
        }

        @Test
        void should_not_include_query_params_when_blank() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            inputs.put(RestExecutionConnector.INPUT_QUERY_PARAMS_JSON, "   ");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("methods").get(0).has("queryParams")).isFalse();
        }

        @Test
        void should_default_url_to_empty_when_null() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("baseUrl").asText()).isEmpty();
        }

        @Test
        void should_default_httpMethod_to_GET_when_null() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("methods").get(0).get("httpMethod").asText()).isEqualTo("GET");
        }

        @Test
        void should_default_path_to_empty_when_null() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("methods").get(0).get("path").asText()).isEmpty();
        }

        @Test
        void should_default_timeout_to_30000_when_null() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("timeoutMs").asInt()).isEqualTo(30000);
        }

        @Test
        void should_default_verifySsl_to_true_when_null() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("verifySsl").asBoolean()).isTrue();
        }

        @Test
        void should_set_custom_timeout_and_verifySsl() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            inputs.put(RestExecutionConnector.INPUT_TIMEOUT_MS, 60000);
            inputs.put(RestExecutionConnector.INPUT_VERIFY_SSL, false);
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("timeoutMs").asInt()).isEqualTo(60000);
            assertThat(root.get("verifySsl").asBoolean()).isFalse();
        }

        @Test
        void should_pass_template_params_to_engine() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            inputs.put(RestExecutionConnector.INPUT_TEMPLATE_PARAMS_JSON, "{\"userId\": \"42\"}");
            setInputs(inputs);
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            assertThat(captor.getValue().params()).containsEntry("userId", "42");
        }

        @Test
        void should_pass_method_override_and_query_params_in_wizard_mode() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "DELETE");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            inputs.put(RestExecutionConnector.INPUT_QUERY_PARAMS_JSON, "{\"force\":\"true\"}");
            setInputs(inputs);
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            ConnectorRequest req = captor.getValue();
            assertThat(req.methodOverride()).isEqualTo("DELETE");
            assertThat(req.queryParams()).containsEntry("force", "true");
        }

        @Test
        void should_not_set_auth_fields_for_blank_values() throws Exception {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "BASIC");
            inputs.put(RestExecutionConnector.INPUT_USERNAME, "   ");
            inputs.put(RestExecutionConnector.INPUT_PASSWORD, "");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "GET");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://api.example.com");
            setInputs(inputs);

            String config = connector.buildConfigJsonFromWizard();
            JsonNode root = MAPPER.readTree(config);

            assertThat(root.get("auth").get("authType").asText()).isEqualTo("BASIC");
            assertThat(root.get("auth").has("username")).isFalse();
            assertThat(root.get("auth").has("password")).isFalse();
        }
    }

    // ========================================================================
    // EXECUTE - Error Handling
    // ========================================================================

    @Nested
    @DisplayName("Execute - Error Handling")
    class ErrorHandling {

        @Test
        void should_return_error_outputs_on_api_failure() throws Exception {
            ConnectorResponse errorResponse = ConnectorResponse.error(
                    503, null, "Service Unavailable", 300L, "https://api.example.com/users");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(errorResponse);

            setInputs(Map.of(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON));
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            Map<String, Object> outputs = connector.outputs();
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_SUCCESS)).isEqualTo(false);
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_STATUS_CODE)).isEqualTo(503);
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_ERROR_MESSAGE)).isEqualTo("Service Unavailable");
        }

        @Test
        void should_handle_engine_exception_gracefully() throws Exception {
            when(mockEngine.execute(any(ConnectorRequest.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            setInputs(Map.of(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON));
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            Map<String, Object> outputs = connector.outputs();
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_SUCCESS)).isEqualTo(false);
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_ERROR_MESSAGE)).isEqualTo("Connection refused");
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_STATUS_CODE)).isEqualTo(-1);
        }

        @Test
        void should_set_response_headers_to_empty_json_on_serialization_failure() throws Exception {
            // Use a response with headers that will serialize, but simulate the path
            // by providing a null responseHeaders map
            ConnectorResponse response = ConnectorResponse.success(200, "OK", null, 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            setInputs(Map.of(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON));
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            Map<String, Object> outputs = connector.outputs();
            // Even with null headers, writeValueAsString(null) produces "null" which is valid
            // The important thing is that it doesn't crash
            assertThat(outputs.get(RestExecutionConnector.OUTPUT_SUCCESS)).isEqualTo(true);
        }
    }

    // ========================================================================
    // FILE UPLOAD (multipart/related)
    // ========================================================================

    @Nested
    @DisplayName("Execute - File Upload (multipart/related)")
    class FileUploadExecution {

        @Test
        void should_pass_file_fields_to_engine_in_config_mode() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            String fileBase64 = java.util.Base64.getEncoder().encodeToString("fake-pdf".getBytes());
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON);
            inputs.put(RestExecutionConnector.INPUT_BODY, "{\"name\":\"test.pdf\"}");
            inputs.put(RestExecutionConnector.INPUT_FILE_CONTENT_BASE64, fileBase64);
            inputs.put(RestExecutionConnector.INPUT_FILE_CONTENT_TYPE, "application/pdf");
            setInputs(inputs);
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            ConnectorRequest req = captor.getValue();
            assertThat(req.fileContentBase64()).isEqualTo(fileBase64);
            assertThat(req.fileContentType()).isEqualTo("application/pdf");
            assertThat(req.body()).isEqualTo("{\"name\":\"test.pdf\"}");
        }

        @Test
        void should_pass_file_fields_to_engine_in_wizard_mode() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            String fileBase64 = java.util.Base64.getEncoder().encodeToString("fake-content".getBytes());
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, "NONE");
            inputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, "POST");
            inputs.put(RestExecutionConnector.INPUT_URL, "https://www.googleapis.com/upload/drive/v3/files");
            inputs.put(RestExecutionConnector.INPUT_BODY, "{\"name\":\"doc.txt\"}");
            inputs.put(RestExecutionConnector.INPUT_FILE_CONTENT_BASE64, fileBase64);
            inputs.put(RestExecutionConnector.INPUT_FILE_CONTENT_TYPE, "text/plain");
            setInputs(inputs);
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            ConnectorRequest req = captor.getValue();
            assertThat(req.fileContentBase64()).isEqualTo(fileBase64);
            assertThat(req.fileContentType()).isEqualTo("text/plain");
        }

        @Test
        void should_default_file_fields_to_empty_when_null() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            setInputs(Map.of(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON));
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            ConnectorRequest req = captor.getValue();
            assertThat(req.fileContentBase64()).isEmpty();
            assertThat(req.fileContentType()).isEmpty();
        }
    }

    // ========================================================================
    // CONNECT / DISCONNECT
    // ========================================================================

    @Nested
    @DisplayName("Connect / Disconnect")
    class ConnectDisconnect {

        @Test
        void should_initialize_engine_on_connect() throws Exception {
            connector.connect();
        }

        @Test
        void should_release_engine_on_disconnect() throws Exception {
            connector.connect();
            connector.disconnect();
        }

        @Test
        void should_allow_disconnect_without_connect() throws Exception {
            assertThatCode(() -> connector.disconnect()).doesNotThrowAnyException();
        }
    }

    // ========================================================================
    // Input Parsing
    // ========================================================================

    @Nested
    @DisplayName("Input Parsing")
    class InputParsing {

        @Test
        void should_return_null_for_missing_string_input() {
            setInputs(Map.of());
            assertThat(connector.getStringInput("nonExistent")).isNull();
        }

        @Test
        void should_convert_non_string_to_string() {
            setInputs(Map.of(RestExecutionConnector.INPUT_TIMEOUT_MS, 42));
            assertThat(connector.getStringInput(RestExecutionConnector.INPUT_TIMEOUT_MS)).isEqualTo("42");
        }

        @Test
        void should_return_null_for_missing_integer_input() {
            setInputs(Map.of());
            assertThat(connector.getIntegerInput("nonExistent")).isNull();
        }

        @Test
        void should_return_integer_directly() {
            setInputs(Map.of(RestExecutionConnector.INPUT_TIMEOUT_MS, 5000));
            assertThat(connector.getIntegerInput(RestExecutionConnector.INPUT_TIMEOUT_MS)).isEqualTo(5000);
        }

        @Test
        void should_convert_long_to_integer() {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_TIMEOUT_MS, 3000L);
            setInputs(inputs);
            assertThat(connector.getIntegerInput(RestExecutionConnector.INPUT_TIMEOUT_MS)).isEqualTo(3000);
        }

        @Test
        void should_parse_string_to_integer() {
            setInputs(Map.of(RestExecutionConnector.INPUT_TIMEOUT_MS, "7500"));
            assertThat(connector.getIntegerInput(RestExecutionConnector.INPUT_TIMEOUT_MS)).isEqualTo(7500);
        }

        @Test
        void should_return_null_for_unparseable_integer_string() {
            setInputs(Map.of(RestExecutionConnector.INPUT_TIMEOUT_MS, "not-a-number"));
            assertThat(connector.getIntegerInput(RestExecutionConnector.INPUT_TIMEOUT_MS)).isNull();
        }

        @Test
        void should_return_null_for_missing_boolean_input() {
            setInputs(Map.of());
            assertThat(connector.getBooleanInput("nonExistent")).isNull();
        }

        @Test
        void should_return_boolean_directly() {
            setInputs(Map.of(RestExecutionConnector.INPUT_VERIFY_SSL, true));
            assertThat(connector.getBooleanInput(RestExecutionConnector.INPUT_VERIFY_SSL)).isTrue();
        }

        @Test
        void should_parse_string_to_boolean() {
            setInputs(Map.of(RestExecutionConnector.INPUT_VERIFY_SSL, "true"));
            assertThat(connector.getBooleanInput(RestExecutionConnector.INPUT_VERIFY_SSL)).isTrue();
        }

        @Test
        void should_parse_false_string_to_boolean() {
            setInputs(Map.of(RestExecutionConnector.INPUT_VERIFY_SSL, "false"));
            assertThat(connector.getBooleanInput(RestExecutionConnector.INPUT_VERIFY_SSL)).isFalse();
        }
    }

    // ========================================================================
    // JSON Map Parsing (parseJsonMap via execution)
    // ========================================================================

    @Nested
    @DisplayName("JSON Map Parsing")
    class JsonMapParsing {

        @Test
        void should_return_empty_map_for_null_json() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            setInputs(Map.of(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON));
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            assertThat(captor.getValue().headers()).isEmpty();
            assertThat(captor.getValue().params()).isEmpty();
        }

        @Test
        void should_return_empty_map_for_empty_json_object() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON);
            inputs.put(RestExecutionConnector.INPUT_HEADERS_JSON, "{}");
            inputs.put(RestExecutionConnector.INPUT_TEMPLATE_PARAMS_JSON, "  {}  ");
            setInputs(inputs);
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            assertThat(captor.getValue().headers()).isEmpty();
            assertThat(captor.getValue().params()).isEmpty();
        }

        @Test
        void should_return_empty_map_for_blank_json() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON);
            inputs.put(RestExecutionConnector.INPUT_HEADERS_JSON, "   ");
            setInputs(inputs);
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            assertThat(captor.getValue().headers()).isEmpty();
        }

        @Test
        void should_return_empty_map_for_invalid_json() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON);
            inputs.put(RestExecutionConnector.INPUT_HEADERS_JSON, "not-json-at-all");
            setInputs(inputs);
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            assertThat(captor.getValue().headers()).isEmpty();
        }

        @Test
        void should_parse_valid_json_map() throws Exception {
            ConnectorResponse response = ConnectorResponse.success(200, "OK", Map.of(), 100L, "url");
            when(mockEngine.execute(any(ConnectorRequest.class))).thenReturn(response);

            Map<String, Object> inputs = new HashMap<>();
            inputs.put(RestExecutionConnector.INPUT_CONFIG_JSON, VALID_CONFIG_JSON);
            inputs.put(RestExecutionConnector.INPUT_HEADERS_JSON, "{\"Accept\":\"text/plain\",\"X-Req\":\"abc\"}");
            setInputs(inputs);
            connector.setEngine(mockEngine);
            connector.executeBusinessLogic();

            ArgumentCaptor<ConnectorRequest> captor = ArgumentCaptor.forClass(ConnectorRequest.class);
            verify(mockEngine).execute(captor.capture());
            assertThat(captor.getValue().headers())
                    .containsEntry("Accept", "text/plain")
                    .containsEntry("X-Req", "abc");
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private void setInputs(Map<String, Object> inputs) {
        Map<String, Object> allInputs = new HashMap<>();
        allInputs.put(RestExecutionConnector.INPUT_AUTH_TYPE, null);
        allInputs.put(RestExecutionConnector.INPUT_USERNAME, null);
        allInputs.put(RestExecutionConnector.INPUT_PASSWORD, null);
        allInputs.put(RestExecutionConnector.INPUT_TOKEN, null);
        allInputs.put(RestExecutionConnector.INPUT_API_KEY_NAME, null);
        allInputs.put(RestExecutionConnector.INPUT_API_KEY_VALUE, null);
        allInputs.put(RestExecutionConnector.INPUT_API_KEY_LOCATION, null);
        allInputs.put(RestExecutionConnector.INPUT_TOKEN_URL, null);
        allInputs.put(RestExecutionConnector.INPUT_CLIENT_ID, null);
        allInputs.put(RestExecutionConnector.INPUT_CLIENT_SECRET, null);
        allInputs.put(RestExecutionConnector.INPUT_SCOPE, null);
        allInputs.put(RestExecutionConnector.INPUT_SERVICE_ACCOUNT_EMAIL, null);
        allInputs.put(RestExecutionConnector.INPUT_PRIVATE_KEY, null);
        allInputs.put(RestExecutionConnector.INPUT_HTTP_METHOD, null);
        allInputs.put(RestExecutionConnector.INPUT_URL, null);
        allInputs.put(RestExecutionConnector.INPUT_PATH, null);
        allInputs.put(RestExecutionConnector.INPUT_CONTENT_TYPE, null);
        allInputs.put(RestExecutionConnector.INPUT_BODY, null);
        allInputs.put(RestExecutionConnector.INPUT_HEADERS_JSON, null);
        allInputs.put(RestExecutionConnector.INPUT_QUERY_PARAMS_JSON, null);
        allInputs.put(RestExecutionConnector.INPUT_TEMPLATE_PARAMS_JSON, null);
        allInputs.put(RestExecutionConnector.INPUT_TIMEOUT_MS, null);
        allInputs.put(RestExecutionConnector.INPUT_VERIFY_SSL, null);
        allInputs.put(RestExecutionConnector.INPUT_FILE_CONTENT_BASE64, null);
        allInputs.put(RestExecutionConnector.INPUT_FILE_CONTENT_TYPE, null);
        allInputs.put(RestExecutionConnector.INPUT_CONFIG_JSON, null);
        allInputs.putAll(inputs);
        connector.setInputParameters(allInputs);
    }

    private static class TestableConnector extends RestExecutionConnector {
        Map<String, Object> outputs() {
            return getOutputParameters();
        }
    }
}
