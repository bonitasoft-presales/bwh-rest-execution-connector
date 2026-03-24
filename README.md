# BWH REST Execution Connector

Multi-connector for executing REST API calls with built-in authentication management.
Delegates execution logic to `ConnectorExecutionEngine` from the [shared library](https://github.com/bonitasoft-presales/process-builder-extension-library).

## Features

- **6 authentication types**: NONE, BASIC, BEARER, API_KEY, OAuth2 Client Credentials, OAuth2 Password
- **Two usage modes**: Config JSON (recommended) and Wizard (manual)
- **Template substitution**: Dynamic URL/path/body parameter replacement
- **SSL control**: Toggle certificate verification for self-signed environments
- **Full response metadata**: Status code, headers, body, execution time, final URL

## Requirements

| Dependency | Version |
|---|---|
| Java | 17+ |
| Bonita Runtime | 10.2.0+ |
| [process-builder-extension-library](https://github.com/bonitasoft-presales/process-builder-extension-library) | 0.0.0.91+ |

## Installation

### Import into Bonita Studio

1. Build the connector ZIP:
   ```bash
   ./mvnw clean package
   ```
2. The importable ZIP is generated at:
   ```
   target/bwh-rest-execution-connector-<version>-impl.zip
   ```
3. In Bonita Studio: **Development > Connector > Import...** → select the ZIP file.

### ZIP contents

```
bwh-rest-execution-connector-<version>-impl.zip
├── bwh-rest-execution.def          # Connector definition
├── bwh-rest-execution.impl         # Implementation descriptor + dependency list
├── bwh-rest-execution.properties   # UI labels (i18n)
├── connector.png                   # Icon
└── classpath/
    ├── bwh-rest-execution-connector-<version>.jar
    ├── process-builder-extension-library-<version>.jar
    └── (transitive runtime dependencies)
```

## Usage

### Mode 1: Config JSON (Recommended)

The connector accepts a full `PBConfiguration` JSON object that defines the entire REST call — base URL, authentication, HTTP method, headers, query params, and more.

**Inputs:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `configJson` | String | Yes | Full PBConfiguration JSON |
| `verifySsl` | Boolean | No (default: `true`) | Enable/disable SSL certificate verification |

**Example `configJson`:**

```json
{
  "baseUrl": "https://api.example.com",
  "methods": [
    {
      "name": "default",
      "httpMethod": "POST",
      "path": "/users/{userId}/orders",
      "headers": {
        "Content-Type": "application/json"
      },
      "queryParams": {
        "page": "1",
        "limit": "10"
      }
    }
  ],
  "auth": {
    "authType": "BEARER",
    "token": "eyJhbGciOiJIUzI1NiIs..."
  },
  "timeoutMs": 30000,
  "verifySsl": true
}
```

### Mode 2: Wizard (Manual Configuration)

When `configJson` is empty, the connector uses individual input parameters to build the request.

#### Authentication parameters

| Parameter | Type | Used by |
|---|---|---|
| `authType` | String | All (NONE, BASIC, BEARER, API_KEY, OAUTH2_CLIENT_CREDENTIALS, OAUTH2_PASSWORD) |
| `username` | String | BASIC, OAUTH2_PASSWORD |
| `password` | String | BASIC, OAUTH2_PASSWORD |
| `token` | String | BEARER |
| `apiKeyName` | String | API_KEY (e.g., `X-API-Key`) |
| `apiKeyValue` | String | API_KEY |
| `apiKeyLocation` | String | API_KEY (HEADER, QUERY) |
| `tokenUrl` | String | OAUTH2_* |
| `clientId` | String | OAUTH2_* |
| `clientSecret` | String | OAUTH2_* |
| `scope` | String | OAUTH2_* |

#### Request parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `httpMethod` | String | Yes | GET, POST, PUT, PATCH, DELETE |
| `url` | String | Yes | Base URL of the endpoint |
| `path` | String | No | Additional path (e.g., `/users/{userId}`) |
| `contentType` | String | No | Content-Type header value |
| `body` | String | No | Request body (JSON, XML, etc.) |
| `headersJson` | String (JSON) | No | Additional headers as JSON map |
| `queryParamsJson` | String (JSON) | No | Query parameters as JSON map |
| `templateParamsJson` | String (JSON) | No | URL/path template substitution parameters |
| `timeoutMs` | Integer | No (default: 30000) | Timeout in milliseconds |
| `verifySsl` | Boolean | No (default: `true`) | SSL certificate verification |

**Example `headersJson`:**
```json
{"Authorization": "Bearer xyz", "X-Custom-Header": "value"}
```

**Example `templateParamsJson`:**
```json
{"userId": "123", "orgId": "456"}
```
With path `/users/{userId}/orgs/{orgId}` resolves to `/users/123/orgs/456`.

## Outputs

| Parameter | Type | Description |
|---|---|---|
| `success` | Boolean | `true` if HTTP status is 2xx |
| `statusCode` | Integer | HTTP response status code |
| `responseBody` | String | Response body content |
| `responseHeaders` | String (JSON) | Response headers as JSON |
| `errorMessage` | String | Error description (if applicable) |
| `executionTimeMs` | Long | Execution time in milliseconds |
| `requestUrl` | String | Final URL after template substitution |

## Authentication Details

### NONE
No authentication headers are added.

### BASIC
Sends `Authorization: Basic <base64(username:password)>` header.

### BEARER
Sends `Authorization: Bearer <token>` header.

### API_KEY
Adds the API key as a header or query parameter depending on `apiKeyLocation`:
- `HEADER`: Adds `<apiKeyName>: <apiKeyValue>` header
- `QUERY`: Appends `?<apiKeyName>=<apiKeyValue>` to the URL

### OAUTH2_CLIENT_CREDENTIALS
Requests an access token from `tokenUrl` using client credentials grant. Token caching and refresh are handled by the shared library.

### OAUTH2_PASSWORD
Requests an access token from `tokenUrl` using resource owner password credentials grant.

## Connector Lifecycle

The connector follows the standard Bonita connector lifecycle:

1. **VALIDATE** — Validates inputs (JSON format, required fields, timeout >= 0)
2. **CONNECT** — Initializes `ConnectorExecutionEngine`
3. **EXECUTE** — Builds `ConnectorRequest`, delegates to engine, maps response to outputs
4. **DISCONNECT** — Releases engine resources

## Build

```bash
# Standard build
./mvnw clean package

# Run tests only
./mvnw test

# CI build (optimized jqwik iterations + parallel tests)
./mvnw clean verify site -Pci

# Skip tests
./mvnw clean package -Pquick
```

## CI/CD

GitHub Actions workflows are included:

| Workflow | Trigger | Description |
|---|---|---|
| `build.yml` | Push/PR to `main` | Build, test, publish to GitHub Packages, deploy reports to Pages |
| `tagAndRelease.yml` | Manual | Tag version and create GitHub Release |
| `mutation.yml` | Manual | Run PIT mutation testing |
| `cleanup.yml` | Manual | Cleanup old package versions |

## Project Structure

```
src/
├── main/
│   ├── java/.../connector/
│   │   └── RestExecutionConnector.java    # Main connector class
│   ├── resources/
│   │   ├── bwh-rest-execution.def         # Connector definition (inputs/outputs/UI)
│   │   ├── bwh-rest-execution.properties  # UI labels
│   │   └── connector.png                  # Connector icon
│   └── resources-filtered/
│       └── bwh-rest-execution.impl        # Implementation descriptor (Maven-filtered)
├── script/
│   └── dependencies-as-var.groovy         # Generates jar dependency list for .impl
├── test/
│   └── .../connector/
│       └── RestExecutionConnectorTest.java
└── assembly/
    └── connector-assembly.xml             # ZIP packaging descriptor
```
