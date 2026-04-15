# BWH REST Execution Connector

[![Java](https://img.shields.io/badge/Java-17%2B-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Bonita Runtime](https://img.shields.io/badge/Bonita%20Runtime-10.2.0%2B-1BA7E6)](https://documentation.bonitasoft.com/)
[![Build](https://github.com/bonitasoft-presales/bwh-rest-execution-connector/actions/workflows/build.yml/badge.svg)](https://github.com/bonitasoft-presales/bwh-rest-execution-connector/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/bonitasoft-presales/bwh-rest-execution-connector?include_prereleases)](https://github.com/bonitasoft-presales/bwh-rest-execution-connector/releases)
[![License](https://img.shields.io/badge/License-GPL--2.0-blue.svg)](./LICENSE)

A **configuration-driven REST connector** for Bonita BPM. A single connector artifact handles arbitrary HTTP/HTTPS calls against external services, bundling authentication, templating, SSL control and rich response metadata. All runtime behaviour is described by a JSON payload (`configJson`) rather than by multiple dedicated Studio parameters, which keeps process diagrams clean and lets you swap endpoints, credentials or payloads without redesigning the diagram.

Execution logic is delegated to `ConnectorExecutionEngine` from the [process-builder-extension-library](https://github.com/bonitasoft-presales/process-builder-extension-library), so authentication, templating and HTTP transport are shared — and tested — across the whole Process Builder ecosystem.

---

## Table of Contents

1. [Problem It Solves](#1-problem-it-solves)
2. [Features](#2-features)
3. [Prerequisites](#3-prerequisites)
4. [Installation](#4-installation)
5. [Inputs](#5-inputs)
6. [`configJson` Structure](#6-configjson-structure)
7. [Authentication Modes](#7-authentication-modes)
8. [Template Substitution](#8-template-substitution)
9. [Outputs](#9-outputs)
10. [Connector Lifecycle](#10-connector-lifecycle)
11. [Build](#11-build)
12. [CI/CD](#12-cicd)
13. [Project Structure](#13-project-structure)
14. [Troubleshooting](#14-troubleshooting)
15. [Limitations](#15-limitations)
16. [Contributing](#16-contributing)
17. [License](#17-license)
18. [Links](#18-links)

---

## 1. Problem It Solves

Out-of-the-box Bonita connectors for REST calls force designers to pick a specific connector per authentication scheme (Basic, OAuth2, API key, …), to wire every header and parameter manually, and to duplicate configuration across tasks. In long-running integration projects this leads to:

- **Diagram bloat** — one REST call becomes several connector boxes if the auth type changes.
- **Credential duplication** — tokens and base URLs copied into every task.
- **Hard-to-audit integrations** — no single place to see what the process truly sends.

`bwh-rest-execution-connector` replaces that sprawl with **one connector whose full contract lives in a JSON configuration**. The connector reads the configuration, selects the right authentication strategy, resolves placeholders, issues the HTTP request and returns a normalized response envelope.

## 2. Features

- **Six authentication types** in a single connector: `NONE`, `BASIC`, `BEARER`, `API_KEY`, OAuth2 *Client Credentials* and OAuth2 *Password* grants.
- **Configuration-as-JSON**: the full request — base URL, methods, headers, query parameters, body, timeouts — is encoded in one `configJson` string.
- **Template substitution**: path, query and body values may reference runtime variables via the `{placeholder}` syntax, resolved at execution time.
- **Full response envelope**: status code, body, headers, success flag, error message, execution time and effective request URL are returned as separate outputs.
- **SSL control**: a dedicated `verifySsl` input toggles certificate validation for self-signed or internal environments.
- **Clear lifecycle** aligned with Bonita standards: `VALIDATE → CONNECT → EXECUTE → DISCONNECT`.
- **Studio-friendly**: ships as a single importable ZIP.

## 3. Prerequisites

| Requirement | Minimum version |
|-------------|-----------------|
| JDK | Java 17 |
| Bonita Runtime | 10.2.0 |
| Bonita Studio | Release compatible with the runtime above |
| Maven Wrapper | Bundled (`./mvnw`) — no global install required |
| [`process-builder-extension-library`](https://github.com/bonitasoft-presales/process-builder-extension-library) | 0.0.0.92 or later, available on the runtime classpath |

Internet access from the Bonita node is required for remote calls. When behind a corporate proxy, configure the JVM system properties (`http.proxyHost`, `https.proxyHost`, etc.) on the Bonita runtime.

## 4. Installation

### 4.1. Build the connector ZIP

```bash
./mvnw clean package
```

The importable ZIP is generated at:

```
target/bwh-rest-execution-connector-<version>-impl.zip
```

### 4.2. ZIP contents

```
bwh-rest-execution-connector-<version>-impl.zip
├── bwh-rest-execution.def          # Connector definition (inputs, outputs, UI)
├── bwh-rest-execution.impl         # Implementation descriptor + dependency list
├── bwh-rest-execution.properties   # UI labels (i18n)
├── connector.png                   # Icon
└── classpath/
    ├── bwh-rest-execution-connector-<version>.jar
    ├── process-builder-extension-library-<version>.jar
    └── (transitive runtime dependencies)
```

### 4.3. Import into Bonita Studio

1. Open Bonita Studio.
2. Go to **Development → Connectors → Import**.
3. Select the ZIP produced above.
4. The connector is registered in the current workspace and becomes available from the Service Task connector picker.

### 4.4. Deploy to a running Bonita runtime

Package your Bonita project as usual (`.bar` file) with the connector selected and deploy it through the Bonita Portal or via the Bonita CLI. The extension library JAR must be available on the runtime classpath.

## 5. Inputs

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `configJson` | `String` (JSON) | Yes | — | Serialized `PBConfiguration` object containing auth, baseUrl, methods, headers and all overrides. |
| `verifySsl` | `Boolean` | No | `true` | Enable or disable SSL certificate verification. Disable only in controlled non-production environments. |

> The `configJson` payload is typically produced by the `configJson()` Groovy helper in `PE_RestAPIConnector`, which resolves the configuration from `PBActionContent.contentRef` and process parameters.

## 6. `configJson` Structure

The `configJson` parameter encapsulates the entire REST call configuration:

```json
{
  "baseUrl": "https://api.example.com",
  "methods": [
    {
      "name": "default",
      "httpMethod": "POST",
      "path": "/users/{userId}/orders",
      "headers": {
        "Content-Type": "application/json",
        "X-Custom-Header": "value"
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

> The authoritative schema is published by `process-builder-extension-library` at https://bonitasoft-presales.github.io/process-builder-extension-library/schema-doc/index.html.

## 7. Authentication Modes

All authentication configuration is embedded in the `auth` section of `configJson`. The `ConnectorExecutionEngine` handles token acquisition, caching and refresh automatically.

### 7.1. `NONE` — No authentication

```json
{ "authType": "NONE" }
```

### 7.2. `BASIC` — HTTP Basic Auth

```json
{ "authType": "BASIC", "username": "user", "password": "pass" }
```

### 7.3. `BEARER` — Bearer token

```json
{ "authType": "BEARER", "token": "eyJhbGciOi..." }
```

### 7.4. `API_KEY` — API key in header or query parameter

```json
{
  "authType": "API_KEY",
  "keyName": "X-API-Key",
  "keyValue": "abc123",
  "location": "HEADER"
}
```

`location` accepts `HEADER` or `QUERY`.

### 7.5. `OAUTH2_CLIENT_CREDENTIALS` — OAuth2 Client Credentials grant

```json
{
  "authType": "OAUTH2_CLIENT_CREDENTIALS",
  "tokenUrl": "https://auth.example.com/oauth/token",
  "clientId": "my-client",
  "clientSecret": "secret",
  "scope": "read write"
}
```

### 7.6. `OAUTH2_PASSWORD` — OAuth2 Resource Owner Password grant

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

## 8. Template Substitution

Any string value inside `configJson` — typically URL paths, query parameters and body fields — may contain `{placeholderName}` tokens. Placeholders are substituted from the connector's runtime context before the request is built.

For example, with the path `/users/{userId}/orgs/{orgId}` and template parameters `{"userId": "123", "orgId": "456"}`, the final path resolves to `/users/123/orgs/456`. Undefined placeholders fail the `VALIDATE` phase with a descriptive error rather than silently sending a malformed URL.

## 9. Outputs

| Output | Type | Description |
|--------|------|-------------|
| `success` | `Boolean` | `true` when the HTTP status code is 2xx and no protocol error occurred. |
| `statusCode` | `Integer` | HTTP status code returned by the server. |
| `responseBody` | `String` | Raw response payload (usually JSON; parse downstream if needed). |
| `responseHeaders` | `String` (JSON) | Serialized map of response headers. |
| `errorMessage` | `String` | Populated when `success` is `false`; contains the root cause or the server's error description. |
| `executionTimeMs` | `Long` | Wall-clock time of the HTTP exchange, including authentication round-trips. |
| `requestUrl` | `String` | Fully resolved URL actually sent (base URL + path + query parameters, after template substitution). Useful for auditing. |

## 10. Connector Lifecycle

The connector follows the standard Bonita connector lifecycle:

1. **VALIDATE** — Validates that `configJson` is valid JSON and that `timeoutMs` ≥ 0.
2. **CONNECT** — Initializes `ConnectorExecutionEngine`.
3. **EXECUTE** — Builds a `ConnectorRequest` from `configJson`, delegates to the engine and maps the response to the declared outputs.
4. **DISCONNECT** — Releases engine resources.

## 11. Build

```bash
# Standard build
./mvnw clean package

# Run tests only
./mvnw test

# CI build (optimized jqwik iterations + parallel tests)
./mvnw clean verify site -Pci

# Skip tests (fastest feedback loop)
./mvnw clean package -Pquick
```

## 12. CI/CD

GitHub Actions workflows shipped with the repository:

| Workflow | Trigger | Description |
|----------|---------|-------------|
| `build.yml` | Push / PR to `main` | Build, test, publish to GitHub Packages, deploy reports to Pages. |
| `tagAndRelease.yml` | Manual (`workflow_dispatch`) | Tag the version and create a GitHub Release. |
| `mutation.yml` | Manual | Run PIT mutation testing. |
| `cleanup.yml` | Manual | Clean up old package versions. |

## 13. Project Structure

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
│   └── dependencies-as-var.groovy         # Generates JAR dependency list for the .impl
├── test/
│   └── .../connector/
│       └── RestExecutionConnectorTest.java
└── assembly/
    └── connector-assembly.xml             # ZIP packaging descriptor
```

## 14. Troubleshooting

| Symptom | Likely cause | Resolution |
|---------|--------------|------------|
| `ConnectorValidationException: configJson is not valid JSON` | Malformed JSON passed to the input. | Validate the payload with a JSON linter; check quote escaping in Groovy. |
| `UnknownHostException` or request timeout | Network egress blocked, wrong `baseUrl`, or missing proxy. | Verify DNS from the Bonita host; configure JVM proxy properties on the runtime. |
| `401 Unauthorized` with OAuth2 | Token URL unreachable, wrong `clientId`/`clientSecret`, or scope rejected. | Inspect `errorMessage` and `requestUrl`; test the token endpoint with `curl`. |
| `SSLHandshakeException` | Server certificate not trusted by the JVM's truststore. | Import the CA into the JVM truststore. Do **not** disable `verifySsl` in production. |
| Placeholder not substituted | Variable name mismatch or variable `null` at execution time. | Check the process variable binding and default values. |
| `ClassNotFoundException` for `com.bonitasoft.processbuilder.*` | `process-builder-extension-library` missing from the classpath. | Confirm the library JAR is bundled under `classpath/` in the connector ZIP or present in the runtime `lib/`. |

Enable DEBUG logging for the connector package in the Bonita runtime logging configuration to capture full request metadata (URL, headers, masked credentials, status).

## 15. Limitations

- The connector currently targets **HTTP/1.1**; HTTP/2 negotiation is delegated to the underlying HTTP client and is not explicitly exposed.
- **Streaming** uploads/downloads are not supported; the full response body is buffered in memory.
- Retry policies follow `timeoutMs` and optional retry fields; complex back-off strategies are out of scope and should be implemented at the process level.
- **Mutual TLS (mTLS)** support has not been validated end-to-end in this README — consult the maintainers or the shared library documentation before relying on it.

## 16. Contributing

1. Create a branch from `main` named after the Jira ticket: `<JIRA-ID>_<short-description>`.
2. Follow the code style enforced by Checkstyle, PMD and SpotBugs.
3. Every Java class must ship with unit tests; run `./mvnw clean verify` before opening the PR.
4. Open a Pull Request against `main` and request a review from the Process Builder maintainers.

## 17. License

Distributed under the **GPL-2.0** license. See the `LICENSE` file for the full text.

## 18. Links

- **Project homepage**: https://github.com/bonitasoft-presales/bwh-rest-execution-connector
- **Issue tracker**: https://github.com/bonitasoft-presales/bwh-rest-execution-connector/issues
- **Companion library**: https://github.com/bonitasoft-presales/process-builder-extension-library
- **Configuration JSON Schema**: https://bonitasoft-presales.github.io/process-builder-extension-library/schema-doc/index.html
- **Bonita custom connectors guide**: https://documentation.bonitasoft.com/bonita/latest/software-extensibility/connector-archetype
