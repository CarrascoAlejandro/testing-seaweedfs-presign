---
applyTo: '**'
description: This skill documents the technology stack and dependencies used in Cirrus/ProMujer microservices.
---

# Stack and Dependencies

This skill documents the technology stack and dependencies used in Cirrus/ProMujer microservices.

## When to Use

Reference this skill when:
- Bootstrapping a new microservice from scratch
- Adding new dependencies to an existing service
- Troubleshooting dependency conflicts
- Understanding the purpose of existing dependencies

---

## Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Runtime with modern features (switch expressions, text blocks, records) |
| Spring Boot | 4.0.2 | Application framework |
| SQL Server | - | Primary database |
| Keycloak | - | OAuth2/OIDC identity provider |
| Docker | - | Containerization |
| Maven | 3.x (wrapper) | Build tool |

---

## Core Dependencies

### Spring Boot Starters

```xml
<!-- REST API support -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>

<!-- JPA persistence -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Bean validation (Jakarta) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- OAuth2 Resource Server (JWT validation) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

### Database

```xml
<!-- SQL Server JDBC Driver -->
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <version>8.4.0.jre11</version>
</dependency>
```

### Observability

```xml
<!-- Distributed tracing (Zipkin Brave) -->
<dependency>
    <groupId>io.zipkin.brave</groupId>
    <artifactId>brave</artifactId>
    <version>6.0.3</version>
</dependency>
```

### Internal Library

```xml
<!-- Cirrus commons library -->
<dependency>
    <groupId>bo.cirrus.pocketbank</groupId>
    <artifactId>commons-backend</artifactId>
    <version>1.0.6</version>
</dependency>
```

---

## Commons Library (`commons-backend`)

The internal library provides standardized components used across all microservices.

### `RequestParamDto`

Extracts standard request context from HTTP requests.

```java
// Usage in controller
RequestParamDto requestParamDto = new RequestParamDto(request, tracer);
String token = requestParamDto.getToken();           // Authorization header
String customerId = requestParamDto.getCustomerId(); // x-Customer-id header
String channel = requestParamDto.getChannel();       // x-Channel header
String ruleMode = requestParamDto.getRuleMode();     // x-Rule-Mode header
```

**When to use:** Every REST controller method that needs authentication context.

### `ResponseDto<T>`

Standardized API response wrapper.

```java
// Success response
return ResponseEntity.ok(new ResponseDto<>(data));

// Response structure:
// {
//   "data": { ... },
//   "success": true,
//   "message": null
// }
```

**When to use:** All REST endpoint return types.

### `ServiceException`

Business exception with error code.

```java
// Throwing business exceptions
throw new ServiceException(1000, "Tipo de transferencia no soportado");
throw new ServiceException(1001, "El monto debe ser mayor a cero");

// Convention: Error codes by domain
// For example:
// -> 1000-1099: Transfer errors
// -> 1100-1199: Beneficiary errors
// -> 1200-1299: Account errors
```

**When to use:** Business rule violations, validation failures, expected error conditions.

### `Execute`

Utility for safe external service calls with error handling.

```java
// Execute with automatic error wrapping
ResponseDto<CoreTransferResponseDto> response = Execute.orFail(
    () -> transferCoreService.executeTransfer(token, coreRequest),
    ex -> LOGGER.error("ERROR-SERVICE: executeTransfer failed", ex)
);
```

**When to use:** All external microservice calls where failures should be logged and wrapped.

---

## Build Configuration

### Full `pom.xml` Example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.2</version>
        <relativePath/>
    </parent>
    
    <groupId>bo.cirrus.pocketbank</groupId>
    <artifactId>ms-{service-name}</artifactId>
    <version>1.0.0</version>
    <name>ms-{service-name}</name>
    <description>Microservice for {description}</description>
    
    <properties>
        <java.version>21</java.version>
    </properties>
    
    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        
        <!-- Database -->
        <dependency>
            <groupId>com.microsoft.sqlserver</groupId>
            <artifactId>mssql-jdbc</artifactId>
            <version>8.4.0.jre11</version>
        </dependency>
        
        <!-- Observability -->
        <dependency>
            <groupId>io.zipkin.brave</groupId>
            <artifactId>brave</artifactId>
            <version>6.0.3</version>
        </dependency>
        
        <!-- Internal Library -->
        <dependency>
            <groupId>bo.cirrus.pocketbank</groupId>
            <artifactId>commons-backend</artifactId>
            <version>1.0.6</version>
        </dependency>
        
        <!-- Test Dependencies -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Java 21 Features in Use

This codebase leverages modern Java features:

### Switch Expressions

```java
// Instead of switch statements
public static String toDescription(Integer currencyId) {
    return switch (currencyId) {
        case 1 -> "Bs.";
        case 2 -> "Usd.";
        default -> "Desconocido";
    };
}
```

### Text Blocks

```java
// Multi-line JPQL queries
@Query("""
    SELECT bf FROM BeBeneficiary bf
    WHERE bf.customerId = :customerId
    AND bf.accountNumber = :accountNumber
    AND bf.status = 1
""")
BeBeneficiary findActiveBeneficiary(@Param("customerId") String customerId,
                                    @Param("accountNumber") String accountNumber);
```

### Pattern Matching (where applicable)

```java
if (response instanceof MiddlewareResponse<?> mr && mr.getValidStatusCode()) {
    // Process valid response
}
```
