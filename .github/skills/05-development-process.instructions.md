---
description: This skill documents the build, configuration, and deployment workflow for Cirrus/ProMujer microservices.
---
# Development Process

This skill documents the build, configuration, and deployment workflow for Cirrus/ProMujer microservices.

## When to Use

Reference this skill when:
- Setting up local development environment
- Building and packaging the application
- Configuring application properties
- Building Docker images
- Understanding the container startup process
- Deploying to different environments

---

## Local Development

### Prerequisites

- Java 21 (JDK, not just JRE)
- Maven 3.x (or use included wrapper)
- Docker (for containerized testing)
- Access to SQL Server database
- IDE with Java support (IntelliJ IDEA, VS Code with Java extensions)

### Maven Wrapper

The project includes Maven wrapper scripts. Use these instead of system Maven for consistency:

```bash
# Linux/macOS
./mvnw <command>

# Windows
mvnw.cmd <command>
```

### Common Commands

```bash
# Clean and compile
./mvnw clean compile

# Run tests
./mvnw test

# Package JAR (skip tests for faster builds)
./mvnw clean package -DskipTests

# Run application locally
./mvnw spring-boot:run

# Run with specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### IDE Run Configuration

For IntelliJ IDEA or VS Code, create a run configuration. Example:

- **Main class:** `bo.cirrus.pocketbank.ms_transfer.MsTransferApplication`
- **VM options:** `-Xms256m -Xmx512m`
- **Environment variables:** See Configuration section below
- **Active profiles:** `dev` (or your local profile)

---

## Configuration (`application.yaml`)

### Complete Structure

```yaml
server:
  port: 10001

spring:
  datasource:
    url: jdbc:sqlserver://${DB_HOST:localhost}:${DB_PORT:1433};database=${DB_NAME:pocketbank}
    username: ${DB_USER:sa}
    password: ${DB_PASSWORD:password}
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:5}
      connection-timeout: 60000
      idle-timeout: 300000
      max-lifetime: 600000

  jpa:
    hibernate:
      ddl-auto: none  # Never auto-generate DDL
    show-sql: ${SHOW_SQL:false}
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.SQLServerDialect

# OAuth2 / Keycloak Configuration
security:
  oauth2:
    auth-server-url: ${AUTH_SERVER_URL:https://keycloak.example.com/auth}
    realm: ${AUTH_REALM:PocketBank}
    client-id: ${AUTH_CLIENT_ID:pb-core}
    required-role: ${AUTH_REQUIRED_ROLE:DIGITAL_BANK}

spring.security.oauth2.resourceserver.jwt:
  issuer-uri: ${security.oauth2.auth-server-url}/realms/${security.oauth2.realm}

# External Service URLs
feign:
  portfolio:
    url: ${PORTFOLIO_SERVICE_URL:http://localhost:18860}
  beneficiary:
    url: ${BENEFICIARY_SERVICE_URL:http://localhost:18860}
  middleware:
    url: ${MIDDLEWARE_SERVICE_URL:http://localhost:18860}
  mfa:
    url: ${MFA_SERVICE_URL:http://localhost:18860}
  customer:
    url: ${CUSTOMER_SERVICE_URL:http://localhost:18860}
  core:
    url: ${CORE_SERVICE_URL:http://localhost:18860}
  account:
    url: ${ACCOUNT_SERVICE_URL:http://localhost:18860}

# Business Configuration
code:
  eif: ${CODE_EIF:1034}

transfer:
  block:
    currency:
      usd:
        enabled: ${USD_BLOCKED:false}
        message: "Las transacciones en dólares se encuentran bloqueadas temporalmente"
  validation:
    description:
      min-length: ${DESC_MIN_LENGTH:10}
      amount-threshold-bob: ${AMOUNT_THRESHOLD_BOB:50000}
      amount-threshold-usd: ${AMOUNT_THRESHOLD_USD:10000}
    pcc:
      amount-threshold-bob: ${PCC_THRESHOLD_BOB:50000}
      amount-threshold-usd: ${PCC_THRESHOLD_USD:10000}

# Logging
logging:
  level:
    root: INFO
    bo.cirrus.pocketbank: ${LOG_LEVEL:DEBUG}
    org.springframework.web: INFO
    org.hibernate.SQL: ${SQL_LOG_LEVEL:DEBUG}
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | Database server hostname | `localhost` |
| `DB_PORT` | Database server port | `1433` |
| `DB_NAME` | Database name | `pocketbank` |
| `DB_USER` | Database username | `sa` |
| `DB_PASSWORD` | Database password | - |
| `DB_POOL_SIZE` | Connection pool size | `5` |
| `AUTH_SERVER_URL` | Keycloak server URL | - |
| `AUTH_REALM` | Keycloak realm | `PocketBank` |
| `AUTH_CLIENT_ID` | OAuth2 client ID | `pb-core` |
| `*_SERVICE_URL` | External service base URLs | - |
| `CODE_EIF` | Financial entity identifier | `1034` |
| `LOG_LEVEL` | Application log level | `DEBUG` |

### Profile-Specific Configuration

Create profile-specific files for different environments:

```
src/main/resources/
├── application.yaml           # Base configuration
├── application-dev.yaml       # Development overrides
├── application-staging.yaml   # Staging overrides
└── application-prod.yaml      # Production overrides
```

**Example `application-dev.yaml`:**

```yaml
spring:
  datasource:
    url: jdbc:sqlserver://dev-db.internal:1433;database=pocketbank_dev
  jpa:
    show-sql: true

logging:
  level:
    bo.cirrus.pocketbank: DEBUG
    org.hibernate.SQL: DEBUG
```

---

## Docker Build

### Dockerfile Structure

```dockerfile
# Base image: Lightweight JRE
FROM azul/zulu-openjdk-alpine:21-jre

# Install required utilities
RUN apk add --no-cache bash iputils curl

# Working directory
WORKDIR /app

# Copy startup script
COPY cirrus-start-java.sh /app/cirrus-start-java.sh
RUN chmod +x /app/cirrus-start-java.sh

# Copy exploded JAR contents (better caching)
COPY target/dependency-jars /app/lib
COPY target/classes /app

# Environment variables with defaults
ENV JAVA_XMS=256m
ENV JAVA_XMX=512m
ENV SPRING_PROFILE=default

# Expose port
EXPOSE 10001

# Startup
ENTRYPOINT ["/app/cirrus-start-java.sh"]
```

### Build Script (`build-docker.sh`)

```bash
#!/bin/bash
set -e

# Extract version from pom.xml
VERSION=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
ARTIFACT=$(grep -m1 '<artifactId>' pom.xml | sed 's/.*<artifactId>\(.*\)<\/artifactId>.*/\1/')
IMAGE_NAME="${ARTIFACT}:${VERSION}"

echo "Building ${IMAGE_NAME}..."

# 1. Build JAR
./mvnw clean package -DskipTests

# 2. Prepare Docker context (exploded JAR)
mkdir -p target/dependency-jars
cd target
unzip -o *.jar -d extracted
cp -r extracted/BOOT-INF/lib/* dependency-jars/
cp -r extracted/BOOT-INF/classes/* ../target/classes/ 2>/dev/null || true
cd ..

# 3. Build Docker image
docker build -t ${IMAGE_NAME} .

# Optional: Multi-architecture build (for different deployment targets)
# docker buildx build --platform linux/amd64,linux/arm64 -t ${IMAGE_NAME} .

echo "Built: ${IMAGE_NAME}"
```

### Multi-Architecture Builds

For deployments across different architectures (x86_64, ARM64):

```bash
# Enable buildx
docker buildx create --use

# Build for multiple platforms
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t ${REGISTRY}/${IMAGE_NAME} \
  --push \
  .
```

> **Note:** The container registry (Google Artifact Registry, Amazon ECR, Harbor, etc.) varies by environment. Replace `${REGISTRY}` with your environment's registry URL.

---

## Container Startup

### Startup Script (`cirrus-start-java.sh`)

The startup script handles environment-specific initialization before launching the JVM.

```bash
#!/bin/bash
set -e

echo "=== Starting ${CIRRUS_PRODUCT:-microservice} ==="

# 1. Validate required environment variables
REQUIRED_VARS="SPRING_PROFILE"
for var in $REQUIRED_VARS; do
    if [ -z "${!var}" ]; then
        echo "ERROR: Required environment variable $var is not set"
        exit 1
    fi
done

# 2. SSL Certificate Installation (environment-specific)
# This section adapts based on how certificates are provided:
#
# Option A: Certificates from object storage (S3, GCS, MinIO)
# if [ -n "$CERT_BUCKET" ] && [ -n "$CERT_PATH" ]; then
#     echo "Downloading certificates from ${CERT_BUCKET}..."
#     # Download using appropriate CLI (aws, gsutil, mc)
#     # Install to Java keystore
# fi
#
# Option B: Certificates mounted as volume
# if [ -d "/certs" ]; then
#     echo "Installing certificates from /certs..."
#     for cert in /certs/*.crt; do
#         keytool -import -trustcacerts -keystore $JAVA_HOME/lib/security/cacerts \
#             -storepass changeit -noprompt -alias $(basename $cert) -file $cert
#     done
# fi
#
# Option C: Certificates from secrets manager
# (Implementation depends on secrets manager in use)

# 3. Set JVM options
JAVA_OPTS="-Xms${JAVA_XMS:-256m} -Xmx${JAVA_XMX:-512m}"
JAVA_OPTS="$JAVA_OPTS -Djava.security.egd=file:/dev/./urandom"
JAVA_OPTS="$JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILE}"

# Optional: Enable remote debugging
if [ "$DEBUG_ENABLED" = "true" ]; then
    JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
fi

# 4. Determine main class
MAIN_CLASS=${MAIN_CLASS:-bo.cirrus.pocketbank.ms_transfer.MsTransferApplication}

echo "Starting with profile: ${SPRING_PROFILE}"
echo "JVM options: ${JAVA_OPTS}"

# 5. Launch application
exec java $JAVA_OPTS -cp "/app:/app/lib/*" $MAIN_CLASS
```

### Environment-Specific Certificate Handling

The startup script supports different certificate sources depending on the deployment environment:

| Environment | Certificate Source | Configuration |
|-------------|-------------------|---------------|
| AWS | S3 bucket | `CERT_BUCKET`, `AWS_*` credentials |
| GCP | GCS bucket | `CERT_BUCKET`, service account |
| On-premise | Mounted volume | `/certs` directory |
| Kubernetes | Secrets | Mounted secret volume |

> **Note:** The actual certificate download/installation code is environment-specific. The startup script should be adapted based on your deployment target.

---

## Required Environment Variables

### Container Runtime

| Variable | Required | Description |
|----------|----------|-------------|
| `CIRRUS_PRODUCT` | Yes | Product/service identifier |
| `SPRING_PROFILE` | Yes | Active Spring profile |
| `JAVA_XMS` | No | Initial heap size (default: 256m) |
| `JAVA_XMX` | No | Maximum heap size (default: 512m) |
| `MAIN_CLASS` | No | Entry point class |
| `DEBUG_ENABLED` | No | Enable remote debugging |

### Certificate Management (if applicable)

| Variable | Required | Description |
|----------|----------|-------------|
| `CERT_BUCKET` | Conditional | Object storage bucket for certs |
| `CERT_PATH` | Conditional | Path within bucket |
| Cloud credentials | Conditional | Cloud-specific auth variables |

### Application Configuration

Pass application-specific configuration via environment variables as defined in the `application.yaml` section above.

---

## Deployment Workflow

### Local Development

```bash
# 1. Start local database (if using Docker)
docker run -d -p 1433:1433 -e 'ACCEPT_EULA=Y' -e 'SA_PASSWORD=YourPassword' \
    mcr.microsoft.com/mssql/server:2019-latest

# 2. Run application
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Docker Local Testing

```bash
# 1. Build image
./build-docker.sh

# 2. Run container
docker run -p 10001:10001 \
    -e SPRING_PROFILE=dev \
    -e DB_HOST=host.docker.internal \
    -e DB_PASSWORD=YourPassword \
    ms-transfer:1.0.0
```

### Container Registry Push

```bash
# Tag for registry
docker tag ms-transfer:1.0.0 ${REGISTRY}/ms-transfer:1.0.0

# Push to registry
docker push ${REGISTRY}/ms-transfer:1.0.0
```

> **Note:** Replace `${REGISTRY}` with your container registry URL:
> - Google Artifact Registry: `{region}-docker.pkg.dev/{project}/{repo}`
> - Amazon ECR: `{account}.dkr.ecr.{region}.amazonaws.com`
> - Harbor: `harbor.example.com/{project}`
> - Local registry: `localhost:5000`

---

## Health Checks

### Actuator Endpoints

Spring Boot Actuator provides health endpoints (ensure `spring-boot-starter-actuator` is included):

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Application health |
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | Kubernetes readiness probe |
| `/actuator/info` | Application info |

### Container Health Check

```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:10001/actuator/health || exit 1
```

### Kubernetes Probes

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 10001
  initialDelaySeconds: 60
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 10001
  initialDelaySeconds: 30
  periodSeconds: 5
```

---

## Troubleshooting

### Application Won't Start

1. **Check logs:** `docker logs <container_id>`
2. **Verify environment variables:** All required variables set?
3. **Database connectivity:** Can container reach database?
4. **Port conflicts:** Is port 10001 available?

### Database Connection Issues

```bash
# Test connectivity from container
docker exec -it <container_id> sh
nc -zv ${DB_HOST} ${DB_PORT}
```

### Memory Issues

```bash
# Increase heap size
docker run -e JAVA_XMS=512m -e JAVA_XMX=1024m ...

# Check memory usage
docker stats <container_id>
```

### Certificate Issues

```bash
# Verify certificates in keystore
keytool -list -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit

# Test SSL connectivity
openssl s_client -connect <host>:<port>
```
