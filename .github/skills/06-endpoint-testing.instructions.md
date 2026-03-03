---
description: This skill documents the basics of endpoint testing using Bruno CLI for Cirrus/ProMujer microservices.
applyTo: '**/http/**'
---


# Endpoint Testing

This skill documents the basics of endpoint testing using Bruno CLI for Cirrus/ProMujer microservices.

> **📝 NOTE: This skill is a placeholder.**
> 
> This document covers the foundational concepts for endpoint testing with Bruno. It will be **updated with concrete patterns and examples** once a solid testing workflow is established in production usage.
>
> For now, use this as a starting guide for setting up basic request collections.

## When to Use

Reference this skill when:
- Setting up a new test collection for a microservice
- Writing endpoint tests for new features
- Running automated API tests in CI/CD
- Validating API behavior during development

---

## Folder Structure

Create an `http/` folder at the project root for all Bruno collections:

```
ms-transfer/
├── http/                          # Bruno collections root
│   ├── bruno.json                 # Collection configuration
│   ├── environments/              # Environment configurations
│   │   ├── dev.bru
│   │   ├── staging.bru
│   │   └── prod.bru
│   ├── transfer/                  # Transfer API requests
│   │   ├── request-transfer.bru
│   │   ├── confirm-transfer.bru
│   │   └── get-history.bru
│   └── beneficiary/               # Beneficiary API requests
│       ├── list-beneficiaries.bru
│       └── add-beneficiary.bru
├── src/
├── pom.xml
└── ...
```

---

## Bruno Basics

### Installation

```bash
# Install Bruno CLI globally
npm install -g @usebruno/cli

# Verify installation
bruno --version
```

### Collection Configuration (`bruno.json`)

```json
{
  "version": "1",
  "name": "ms-transfer",
  "type": "collection",
  "ignore": [
    "node_modules",
    ".git"
  ]
}
```

---

## Environment Configuration

### Development Environment (`environments/dev.bru`)

```bru
vars {
  baseUrl: http://localhost:10001
  authUrl: https://keycloak-dev.example.com/auth
  realm: PocketBank-Dev
  clientId: pb-core
}

vars:secret [
  token,
  clientSecret
]
```

### Staging Environment (`environments/staging.bru`)

```bru
vars {
  baseUrl: https://api-staging.example.com
  authUrl: https://keycloak-staging.example.com/auth
  realm: PocketBank-Staging
  clientId: pb-core
}

vars:secret [
  token,
  clientSecret
]
```

---

## Request File Structure

### Basic Request Template

```bru
meta {
  name: Request Transfer
  type: http
  seq: 1
}

post {
  url: {{baseUrl}}/api/v1/transfer/request
  body: json
  auth: bearer
}

auth:bearer {
  token: {{token}}
}

headers {
  Content-Type: application/json
  x-Customer-id: {{customerId}}
  x-Channel: MOBILE
}

body:json {
  {
    "transferType": "OWN",
    "originAccountNumber": "1234567890",
    "targetAccountNumber": "0987654321",
    "amount": 100.00,
    "currencyId": 1,
    "description": "Test transfer"
  }
}
```

### GET Request Example

```bru
meta {
  name: Get Transfer History
  type: http
  seq: 3
}

get {
  url: {{baseUrl}}/api/v1/transfer/history
  body: none
  auth: bearer
}

auth:bearer {
  token: {{token}}
}

headers {
  x-Customer-id: {{customerId}}
}

query {
  accountNumber: 1234567890
  limit: 10
  offset: 0
}
```

### Request with Path Variable

```bru
meta {
  name: Get Transfer Detail
  type: http
  seq: 4
}

get {
  url: {{baseUrl}}/api/v1/transfer/:transferId
  body: none
  auth: bearer
}

auth:bearer {
  token: {{token}}
}

headers {
  x-Customer-id: {{customerId}}
}

vars:pre-request {
  transferId: 12345
}
```

---

## Running Tests

### Run All Requests

```bash
# Run entire collection with dev environment
bruno run http/ --env dev

# Run with specific environment
bruno run http/ --env staging
```

### Run Specific Folder

```bash
# Run only transfer requests
bruno run http/transfer/ --env dev
```

### Run Single Request

```bash
# Run specific request file
bruno run http/transfer/request-transfer.bru --env dev
```

### Output Formats

```bash
# JSON output (for CI/CD parsing)
bruno run http/ --env dev --output json

# JUnit XML (for test reporting)
bruno run http/ --env dev --output junit --output-file results.xml
```

---

## Test Assertions

### Basic Assertions

```bru
meta {
  name: Request Transfer
  type: http
  seq: 1
}

post {
  url: {{baseUrl}}/api/v1/transfer/request
  body: json
  auth: bearer
}

# ... headers and body ...

assert {
  res.status: eq 200
  res.body.success: eq true
  res.body.data.status: eq "2FA_REQUESTED"
}
```

### Response Time Assertion

```bru
assert {
  res.status: eq 200
  res.responseTime: lte 2000
}
```

### Response Structure Assertions

```bru
assert {
  res.status: eq 200
  res.body.success: eq true
  res.body.data: isDefined
  res.body.data.transferId: isNumber
  res.body.data.status: isString
}
```

---

## Scripts

### Pre-Request Script

```bru
script:pre-request {
  // Generate unique reference
  const timestamp = Date.now();
  bru.setVar("uniqueRef", `TEST-${timestamp}`);
  
  // Set dynamic date
  const today = new Date().toISOString().split('T')[0];
  bru.setVar("currentDate", today);
}
```

### Post-Response Script

```bru
script:post-response {
  // Store response values for subsequent requests
  if (res.status === 200) {
    const transferId = res.body.data.transferId;
    bru.setEnvVar("lastTransferId", transferId);
    
    console.log(`Transfer created: ${transferId}`);
  }
}
```

---

## Conventions

### Collection Organization

1. **One collection per microservice** — Each `http/` folder belongs to one service
2. **Organize by API resource** — Group requests by entity (transfer, beneficiary, etc.)
3. **Include success and error cases** — Test both happy path and error scenarios
4. **Name requests clearly** — Use action-oriented names (`request-transfer`, not `post-transfer`)

### Request Naming

| Convention | Example |
|------------|---------|
| Action + Resource | `request-transfer.bru` |
| HTTP verb implied by action | `get-history.bru`, `list-beneficiaries.bru` |
| Error case suffix | `request-transfer-invalid-amount.bru` |
| Snake_case or kebab-case | `request-transfer.bru` or `request_transfer.bru` |

### Sequence Numbers

Use `seq` in meta to control execution order:

```bru
meta {
  name: Request Transfer
  type: http
  seq: 1  # Runs first
}
```

```bru
meta {
  name: Confirm Transfer
  type: http
  seq: 2  # Runs second (uses data from first)
}
```

---

## CI/CD Integration

### Basic Pipeline Step

```yaml
# GitHub Actions example
- name: Run API Tests
  run: |
    npm install -g @usebruno/cli
    bruno run http/ --env ${{ env.ENVIRONMENT }} --output junit --output-file test-results.xml

- name: Publish Test Results
  uses: EnricoMi/publish-unit-test-result-action@v2
  if: always()
  with:
    files: test-results.xml
```

### With Authentication Token

```yaml
- name: Get Auth Token
  run: |
    TOKEN=$(curl -s -X POST "${AUTH_URL}/realms/${REALM}/protocol/openid-connect/token" \
      -d "grant_type=client_credentials" \
      -d "client_id=${CLIENT_ID}" \
      -d "client_secret=${CLIENT_SECRET}" | jq -r '.access_token')
    echo "AUTH_TOKEN=${TOKEN}" >> $GITHUB_ENV

- name: Run API Tests
  run: |
    bruno run http/ --env dev --env-var "token=${{ env.AUTH_TOKEN }}"
```

---

## Example: Complete Transfer Test Flow

### 1. Request Transfer (`transfer/01-request-transfer.bru`)

```bru
meta {
  name: Request Transfer
  type: http
  seq: 1
}

post {
  url: {{baseUrl}}/api/v1/transfer/request
  body: json
  auth: bearer
}

auth:bearer {
  token: {{token}}
}

headers {
  Content-Type: application/json
  x-Customer-id: {{customerId}}
  x-Channel: MOBILE
}

body:json {
  {
    "transferType": "OWN",
    "originAccountNumber": "{{originAccount}}",
    "targetAccountNumber": "{{targetAccount}}",
    "amount": 100.00,
    "currencyId": 1,
    "description": "Bruno test transfer"
  }
}

assert {
  res.status: eq 200
  res.body.success: eq true
  res.body.data.status: eq "2FA_REQUESTED"
}

script:post-response {
  if (res.status === 200) {
    bru.setEnvVar("transferId", res.body.data.transferId);
    bru.setEnvVar("mfaToken", res.body.data.mfaToken);
  }
}
```

### 2. Confirm Transfer (`transfer/02-confirm-transfer.bru`)

```bru
meta {
  name: Confirm Transfer
  type: http
  seq: 2
}

post {
  url: {{baseUrl}}/api/v1/transfer/confirm
  body: json
  auth: bearer
}

auth:bearer {
  token: {{token}}
}

headers {
  Content-Type: application/json
  x-Customer-id: {{customerId}}
}

body:json {
  {
    "transferId": {{transferId}},
    "mfaToken": "{{mfaToken}}",
    "mfaCode": "123456"
  }
}

assert {
  res.status: eq 200
  res.body.success: eq true
  res.body.data.status: in ["2FA_CONFIRMED", "SUCCESSFUL"]
}
```

### 3. Verify Transfer (`transfer/03-get-transfer.bru`)

```bru
meta {
  name: Get Transfer Detail
  type: http
  seq: 3
}

get {
  url: {{baseUrl}}/api/v1/transfer/{{transferId}}
  body: none
  auth: bearer
}

auth:bearer {
  token: {{token}}
}

headers {
  x-Customer-id: {{customerId}}
}

assert {
  res.status: eq 200
  res.body.success: eq true
  res.body.data.transferId: eq {{transferId}}
}
```

---

## Future Updates

This skill will be expanded with:

- [ ] Complete test patterns from production usage
- [ ] Error case test examples
- [ ] Authentication flow automation
- [ ] Data-driven testing patterns
- [ ] Mock server integration
- [ ] Performance test patterns
- [ ] Contract testing guidelines

> **Contributors:** When solid testing patterns emerge from production use, update this document with concrete examples following the same structure as other skill files.
