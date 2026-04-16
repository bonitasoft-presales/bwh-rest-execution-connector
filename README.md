# BWH REST Execution Connector

[![Java](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Bonita Runtime](https://img.shields.io/badge/Bonita%20Runtime-10.2.0%2B-1BA7E6)](https://documentation.bonitasoft.com/)
[![Build](https://github.com/bonitasoft-presales/bwh-rest-execution-connector/actions/workflows/build.yml/badge.svg)](https://github.com/bonitasoft-presales/bwh-rest-execution-connector/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/bonitasoft-presales/bwh-rest-execution-connector?include_prereleases)](https://github.com/bonitasoft-presales/bwh-rest-execution-connector/releases)

A **configuration-driven REST connector** for Bonita BPM that handles arbitrary HTTP/HTTPS calls with built-in authentication, template substitution, file uploads and rich response metadata — all from a single connector artifact.

Execution logic is delegated to `ConnectorExecutionEngine` from [process-builder-extension-library](https://github.com/bonitasoft-presales/process-builder-extension-library).

---

## Table of Contents

1. [Problem It Solves](#1-problem-it-solves)
2. [Features](#2-features)
3. [Prerequisites](#3-prerequisites)
4. [Installation](#4-installation)
5. [Operating Modes](#5-operating-modes)
6. [Studio Inputs (Connector Definition)](#6-studio-inputs-connector-definition)
7. [Config Mode — `configJson` Structure](#7-config-mode--configjson-structure)
8. [Wizard Mode — Individual Inputs](#8-wizard-mode--individual-inputs)
9. [Authentication Types](#9-authentication-types)
10. [Template Substitution](#10-template-substitution)
11. [File Upload (Multipart)](#11-file-upload-multipart)
12. [Outputs](#12-outputs)
13. [Connector Lifecycle](#13-connector-lifecycle)
14. [Build](#14-build)
15. [CI/CD](#15-cicd)
16. [Project Structure](#16-project-structure)
17. [Troubleshooting](#17-troubleshooting)
18. [Limitations](#18-limitations)
19. [Contributing](#19-contributing)
20. [Links](#20-links)

---

## 1. Problem It Solves

Standard Bonita REST connectors require one connector per authentication scheme, manual wiring of every header and parameter, and duplicated configuration across tasks. This connector replaces that with **one artifact** whose full request contract lives in a JSON configuration, keeping diagrams clean and configurations auditable.

## 2. Features

- **Seven authentication types**: `NONE`, `BASIC`, `BEARER`, `API_KEY`, `OAUTH2_CLIENT_CREDENTIALS`, `OAUTH2_PASSWORD` and `OAUTH2_JWT_BEARER`.
- **Dual operating mode**: *Config mode* (single `configJson` with the full `PBConfiguration`) or *Wizard mode* (individual Studio inputs for ad-hoc calls).
- **Template substitution**: dynamic replacement of `{{placeholder}}` tokens in URLs, paths, headers, query parameters and body at execution time.
- **File upload**: multipart/related body assembly for Google Drive, Gmail, Microsoft Graph and similar APIs.
- **Full response envelope**: status code, body, headers, success flag, error message, execution time and resolved URL.
- **SSL control**: dedicated `verifySsl` toggle.
- **Connector lifecycle**: `VALIDATE → CONNECT → EXECUTE → DISCONNECT`.

## 3. Prerequisites

| Requirement | Minimum version |
|-------------|-----------------|
| JDK | 17 |
| Bonita Runtime | 10.2.0 |
| Maven Wrapper | Bundled (`./mvnw`) |
| [process-builder-extension-library](https://github.com/bonitasoft-presales/process-builder-extension-library) | 0.0.0.92+ |

## 4. Installation

### 4.1. Build the connector ZIP

```bash
./mvnw clean package
```

Outputs:

| Artifact | Purpose |
|----------|---------|
| `target/bwh-rest-execution-connector-<version>-impl.zip` | Importable ZIP for Bonita Studio |
| `target/bwh-rest-execution-connector-<version>-bonita.jar` | Fat JAR for Bonita Application dependency resolution |

### 4.2. ZIP contents

```
bwh-rest-execution-connector-<version>-impl.zip
├── bwh-rest-execution.def          # Connector definition (inputs, outputs, UI pages)
├── bwh-rest-execution.impl         # Implementation descriptor + dependency list
├── bwh-rest-execution.properties   # UI labels (i18n)
├── connector.png                   # Icon
└── classpath/
    ├── bwh-rest-execution-connector-<version>.jar
    ├── process-builder-extension-library-<version>.jar
    └── (transitive runtime dependencies)
```

### 4.3. Import into Bonita Studio

**Development → Connectors → Import** → select the ZIP. The connector appears as **PB REST Execution** in the `ProcessBuilder` category.

## 5. Operating Modes

The connector supports two mutually exclusive modes:

| Mode | When | Description |
|------|------|-------------|
| **Config mode** | `configJson` is provided and non-blank | The connector delegates everything to `ConnectorExecutionEngine`. All request details are inside the JSON. This is the standard path used by the Process Builder framework. |
| **Wizard mode** | `configJson` is blank or absent | The connector builds the `PBConfiguration` JSON internally from individual inputs (auth type, URL, method, headers, body, etc.). Useful for ad-hoc one-off REST calls without the full Process Builder configuration. |

Validation rules change with the mode:
- **Config mode**: only validates that `configJson` is parseable JSON.
- **Wizard mode**: requires `url` and `httpMethod` to be non-blank.

## 6. Studio Inputs (Connector Definition)

These are the inputs visible in the Bonita Studio connector wizard (from `bwh-rest-execution.def`):

| Input | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `configJson` | `String` | No | — | Full `PBConfiguration` JSON. When provided, activates *config mode*. |
| `verifySsl` | `Boolean` | No | `true` | SSL certificate verification. Set to `false` only in non-production environments. |
| `fileContentBase64` | `String` | No | — | Base64-encoded file content for multipart uploads. |
| `fileContentType` | `String` | No | — | MIME type of the uploaded file (e.g. `application/pdf`). |
| `fileName` | `String` | No | — | Original filename for the upload. |

## 7. Config Mode — `configJson` Structure

```json
{
  "baseUrl": "https://api.example.com",
  "methods": [
    {
      "name": "default",
      "httpMethod": "POST",
      "path": "/users/{{userId}}/orders",
      "headers": {
        "Content-Type": "application/json",
        "X-Custom-Header": "value"
      },
      "queryParams": {
        "page": "1",
        "limit": "10"
      },
      "bodyTemplate": "{\"orderId\": \"{{orderId}}\"}"
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

The engine resolves `{{placeholder}}` tokens from `templateParams`, applies authentication, builds the HTTP request and returns a normalized response.

> The authoritative schema is published at https://bonitasoft-presales.github.io/process-builder-extension-library/schema-doc/index.html.

## 8. Wizard Mode — Individual Inputs

When `configJson` is blank, the connector reads individual inputs and builds the configuration internally:

### Page 1 — Authentication

| Input | Description |
|-------|-------------|
| `authType` | One of: `NONE`, `BASIC`, `BEARER`, `API_KEY`, `OAUTH2_CLIENT_CREDENTIALS`, `OAUTH2_PASSWORD`, `OAUTH2_JWT_BEARER`. |
| `username` | BASIC or OAUTH2_PASSWORD username. |
| `password` | BASIC or OAUTH2_PASSWORD password. |
| `token` | BEARER token. |
| `apiKeyName` | API_KEY header/query parameter name. |
| `apiKeyValue` | API_KEY value. |
| `apiKeyLocation` | `HEADER` or `QUERY`. |
| `tokenUrl` | OAuth2 / JWT Bearer token endpoint. |
| `clientId` | OAuth2 client identifier. |
| `clientSecret` | OAuth2 client secret. |
| `scope` | OAuth2 / JWT Bearer scope. |
| `serviceAccountEmail` | JWT Bearer service account email. |
| `privateKey` | JWT Bearer private key (PEM). |

### Page 2 — Request

| Input | Description |
|-------|-------------|
| `httpMethod` | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`. **Required.** |
| `url` | Base URL (e.g. `https://api.example.com`). **Required.** |
| `path` | Endpoint path (e.g. `/v1/users`). |
| `contentType` | Content-Type header value. |

### Page 3 — Parameters & Options

| Input | Type | Description |
|-------|------|-------------|
| `body` | String | Request body (JSON, XML, etc.). |
| `headersJson` | JSON map | Additional headers: `{"X-Custom": "value"}`. |
| `queryParamsJson` | JSON map | Query parameters: `{"page": "1"}`. |
| `templateParamsJson` | JSON map | Template variables: `{"userId": "42"}`. |
| `timeoutMs` | Integer | Request timeout in ms. Default: `30000`. Must be >= 0. |

> In wizard mode the connector generates a `configJson` with a single method named `"default"`, then delegates to the same engine.

## 9. Authentication Types

All authentication is embedded in the `auth` section of `configJson` (config mode) or set via individual inputs (wizard mode). The `ConnectorExecutionEngine` handles token acquisition, caching and refresh automatically.

### 9.1. `NONE`

```json
{ "authType": "NONE" }
```

### 9.2. `BASIC`

```json
{ "authType": "BASIC", "username": "user", "password": "pass" }
```

### 9.3. `BEARER`

```json
{ "authType": "BEARER", "token": "eyJhbGciOi..." }
```

### 9.4. `API_KEY`

```json
{
  "authType": "API_KEY",
  "keyName": "X-API-Key",
  "keyValue": "abc123",
  "location": "HEADER"
}
```

`location` accepts `HEADER` or `QUERY`.

### 9.5. `OAUTH2_CLIENT_CREDENTIALS`

```json
{
  "authType": "OAUTH2_CLIENT_CREDENTIALS",
  "tokenUrl": "https://auth.example.com/oauth/token",
  "clientId": "my-client",
  "clientSecret": "secret",
  "scope": "read write"
}
```

### 9.6. `OAUTH2_PASSWORD`

```json
{
  "authType": "OAUTH2_PASSWORD",
  "tokenUrl": "https://auth.example.com/oauth/token",
  "clientId": "my-client",
  "clientSecret": "secret",
  "scope": "read write",
  "username": "user",
  "password": "pass"
}
```

### 9.7. `OAUTH2_JWT_BEARER`

For Google Cloud service accounts and similar JWT-based flows:

```json
{
  "authType": "OAUTH2_JWT_BEARER",
  "tokenUrl": "https://oauth2.googleapis.com/token",
  "serviceAccountEmail": "sa@project.iam.gserviceaccount.com",
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
  "scope": "https://www.googleapis.com/auth/drive"
}
```

## 10. Template Substitution

All string values in the configuration — URLs, paths, headers, query parameters, body templates — support `{{placeholderName}}` tokens (double braces). Placeholders are resolved from the `templateParams` map before the request is built.

**Example:**

Path: `/users/{{userId}}/orgs/{{orgId}}`
Template params: `{"userId": "123", "orgId": "456"}`
Resolved path: `/users/123/orgs/456`

Undefined placeholders remain as-is in the output. Validate your parameter names carefully.

## 11. File Upload (Multipart)

The connector supports `multipart/related` uploads (RFC 2387) for APIs like Google Drive, Gmail and Microsoft Graph. Provide:

| Input | Description |
|-------|-------------|
| `fileContentBase64` | Base64-encoded file bytes. |
| `fileContentType` | MIME type of the file (e.g. `image/png`). |
| `fileName` | Original filename. |

The engine assembles the multipart body with a JSON metadata part and the binary file part. These inputs work in both config and wizard mode.

## 12. Outputs

| Output | Type | Description |
|--------|------|-------------|
| `success` | `Boolean` | `true` when HTTP status is 2xx and no protocol error occurred. |
| `statusCode` | `Integer` | HTTP status code. Returns `-1` when an exception prevents the call. |
| `responseBody` | `String` | Raw response payload. |
| `responseHeaders` | `String` (JSON) | Serialized map of response headers. Defaults to `"{}"` on serialization failure. |
| `errorMessage` | `String` | Populated when `success` is `false`; contains the root cause. |
| `executionTimeMs` | `Long` | Wall-clock time including authentication round-trips. |
| `requestUrl` | `String` | Fully resolved URL sent after template substitution. |

## 13. Connector Lifecycle

1. **VALIDATE**
   - Config mode: validates that `configJson` is parseable JSON.
   - Wizard mode: requires `url` and `httpMethod` to be non-blank.
   - Both: `timeoutMs` must be >= 0 when provided.
2. **CONNECT** — Initializes `ConnectorExecutionEngine`.
3. **EXECUTE** — Builds `ConnectorRequest`, delegates to the engine, maps the response to the declared outputs. Catches all exceptions and sets `success=false`, `statusCode=-1`.
4. **DISCONNECT** — Releases engine resources.

## 14. Build

```bash
# Standard build (compile + test + package ZIP + fat JAR)
./mvnw clean package

# Run tests only
./mvnw test

# CI build (optimized jqwik iterations + parallel tests + site reports)
./mvnw clean verify site -Pci

# Skip tests
./mvnw clean package -Pquick
```

## 15. CI/CD

| Workflow | Trigger | Description |
|----------|---------|-------------|
| `build.yml` | Push / PR to `main` | Build, test, publish to GitHub Packages (push only), deploy reports to Pages (push only). |
| `tagAndRelease.yml` | Manual (`workflow_dispatch`) | Tag the version and create a GitHub Release with JAR + ZIP assets. |
| `mutation.yml` | Manual | Run PIT mutation testing with HTML report. |
| `cleanup.yml` | Manual | Clean up old package versions and releases. |

## 16. Project Structure

```
src/
├── main/
│   ├── java/.../connector/
│   │   └── RestExecutionConnector.java    # Main connector class (dual mode)
│   ├── resources/
│   │   ├── bwh-rest-execution.def         # Connector definition (inputs/outputs/UI)
│   │   ├── bwh-rest-execution.properties  # UI labels
│   │   └── connector.png                  # Connector icon
│   └── resources-filtered/
│       └── bwh-rest-execution.impl        # Implementation descriptor (Maven-filtered)
├── script/
│   └── dependencies-as-var.groovy         # Generates JAR dependency list for the .impl
├── test/
│   └── .../connector/
│       └── RestExecutionConnectorTest.java  # ~75 test cases covering both modes
└── assembly/
    ├── connector-assembly.xml             # ZIP packaging descriptor (Bonita Studio)
    └── bonita-assembly.xml                # Fat JAR packaging descriptor (Bonita App)
```

## 17. Troubleshooting

| Symptom | Likely cause | Resolution |
|---------|--------------|------------|
| `ConnectorValidationException: configJson is not valid JSON` | Malformed JSON. | Validate with a JSON linter; check Groovy quote escaping. |
| `ConnectorValidationException: url is required` | Wizard mode triggered (configJson blank) without URL. | Provide `configJson` or set `url` + `httpMethod`. |
| `UnknownHostException` or timeout | DNS or network issue. | Verify connectivity from the Bonita host; check proxy settings. |
| `401 Unauthorized` with OAuth2 | Bad credentials, wrong `tokenUrl` or rejected scope. | Inspect `errorMessage` and `requestUrl`; test the token endpoint with `curl`. |
| `SSLHandshakeException` | Untrusted server certificate. | Import the CA into the JVM truststore. Do **not** disable `verifySsl` in production. |
| `statusCode = -1` | Exception before the HTTP call (e.g. template error). | Check `errorMessage` for the root cause. |
| `ClassNotFoundException: com.bonitasoft.processbuilder.*` | Extension library missing from classpath. | Confirm the library JAR is in the connector ZIP `classpath/` or in the runtime `lib/`. |

## 18. Limitations

- **HTTP/1.1** only; HTTP/2 negotiation depends on the JVM's `HttpClient`.
- **No streaming**: the full response body is buffered in memory.
- **Multipart**: only `multipart/related` is supported (for metadata + file). Standard `multipart/form-data` is not currently implemented.
- **Retry**: only `timeoutMs` is exposed. Complex back-off strategies should be implemented at the process level.

## 19. Contributing

1. Branch from `main`: `<JIRA-ID>_<short-description>`.
2. Follow Checkstyle, PMD and SpotBugs rules.
3. Add tests for every change; run `./mvnw clean verify` before the PR.
4. Open a Pull Request against `main`.

## 20. Links

- **Project homepage**: https://github.com/bonitasoft-presales/bwh-rest-execution-connector
- **Issue tracker**: https://github.com/bonitasoft-presales/bwh-rest-execution-connector/issues
- **Extension library**: https://github.com/bonitasoft-presales/process-builder-extension-library
- **Configuration JSON Schema**: https://bonitasoft-presales.github.io/process-builder-extension-library/schema-doc/index.html
