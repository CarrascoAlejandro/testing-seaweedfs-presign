---
applyTo: '**'
description: Documentation of architecture layers and package structure for Cirrus/ProMujer microservices.
---
# Architecture Layers

This skill documents the layered package structure used in Cirrus/ProMujer microservices.

## When to Use

Reference this skill when:
- Creating a new feature end-to-end (controller → business logic → persistence)
- Understanding where to place new code
- Reviewing code for architectural compliance
- Onboarding new team members

---

## Package Structure Overview

```
bo.cirrus.pocketbank.ms_{service}/
├── Ms{Service}Application.java    # Main entry point
├── api/                           # REST controllers
├── bl/                            # Business logic
│   └── {feature}/                 # Strategy pattern subdirectories
├── config/                        # Spring configurations
├── dao/
│   ├── domain/                    # JPA entities
│   └── repository/                # Spring Data repositories
├── dto/                           # Data transfer objects
│   ├── {domain}/                  # Organized by domain
│   └── feign/                     # External service DTOs
├── enums/                         # Enumerations
├── services/                      # External HTTP clients
├── util/                          # Utilities
└── validation/                    # Validation components
```

---

## API Layer (`api/`)

### When to Use
Create classes in this layer when exposing REST endpoints to external consumers.

### Naming Convention
- `{Entity}Api.java` (e.g., `TransferApi.java`, `BeneficiaryApi.java`)

### Annotations
- `@RestController` — Marks as REST controller
- `@RequestMapping("/api/v1/{entity}")` — Base path (always versioned)
- HTTP method annotations: `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`

### Complete Example

```java
package bo.cirrus.pocketbank.ms_transfer.api;

import bo.cirrus.pocketbank.ms_transfer.bl.TransferBl;
import bo.cirrus.pocketbank.ms_transfer.dto.transfer.TransferRequestDto;
import bo.cirrus.pocketbank.ms_transfer.dto.transfer.TransferResponseDto;
import bo.cirrus.pocketbank.commons.dto.RequestParamDto;
import bo.cirrus.pocketbank.commons.dto.ResponseDto;
import brave.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfer")
public class TransferApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferApi.class);

    private final TransferBl transferBl;
    private final Tracer tracer;

    // Constructor injection (preferred over @Autowired on field)
    public TransferApi(TransferBl transferBl, Tracer tracer) {
        this.transferBl = transferBl;
        this.tracer = tracer;
    }

    @PostMapping("/request")
    public ResponseEntity<ResponseDto<TransferResponseDto>> requestTransfer(
            HttpServletRequest request,
            @RequestBody TransferRequestDto transferRequestDto) {
        
        LOGGER.info("API: requestTransfer - type: {}", transferRequestDto.getTransferType());
        
        // Extract standard request context
        RequestParamDto requestParamDto = new RequestParamDto(request, tracer);
        String token = requestParamDto.getToken();
        String customerId = requestParamDto.getCustomerId();
        
        // Delegate to business logic
        TransferResponseDto response = transferBl.requestTransfer(
            token, customerId, transferRequestDto);
        
        // Wrap in standard response
        return ResponseEntity.ok(new ResponseDto<>(response));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ResponseDto<TransferResponseDto>> confirmTransfer(
            HttpServletRequest request,
            @RequestBody ConfirmTransferRequestDto confirmDto) {
        
        LOGGER.info("API: confirmTransfer - transferId: {}", confirmDto.getTransferId());
        
        RequestParamDto requestParamDto = new RequestParamDto(request, tracer);
        
        TransferResponseDto response = transferBl.confirmTransfer(
            requestParamDto.getToken(),
            requestParamDto.getCustomerId(),
            confirmDto
        );
        
        return ResponseEntity.ok(new ResponseDto<>(response));
    }

    @GetMapping("/history")
    public ResponseEntity<ResponseDto<List<TransferHistoryDto>>> getTransferHistory(
            HttpServletRequest request,
            @RequestParam("accountNumber") String accountNumber,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        
        LOGGER.info("API: getTransferHistory - account: {}", accountNumber);
        
        RequestParamDto requestParamDto = new RequestParamDto(request, tracer);
        
        List<TransferHistoryDto> history = transferBl.getTransferHistory(
            requestParamDto.getToken(),
            requestParamDto.getCustomerId(),
            accountNumber,
            limit
        );
        
        return ResponseEntity.ok(new ResponseDto<>(history));
    }
}
```

### Controller Pattern Summary

1. Log entry with `API:` prefix
2. Create `RequestParamDto` from request
3. Extract token and customerId
4. Call business logic method
5. Wrap result in `ResponseDto`
6. Return `ResponseEntity.ok()`

---

## Business Logic Layer (`bl/`)

### When to Use
Create classes in this layer for:
- Orchestrating multiple operations (service calls, validations, persistence)
- Applying business rules
- Transforming data between layers

### Naming Conventions
- `{Entity}Bl.java` — Main business logic for an entity
- `{Entity}PersistenceBl.java` — Isolated persistence operations (when transaction boundaries matter)
- `CommonBl.java` — Shared utilities across business logic classes

### Complete Example

```java
package bo.cirrus.pocketbank.ms_transfer.bl;

import bo.cirrus.pocketbank.ms_transfer.bl.transfer.Transfer;
import bo.cirrus.pocketbank.ms_transfer.dao.domain.TfTransfer;
import bo.cirrus.pocketbank.ms_transfer.dto.transfer.*;
import bo.cirrus.pocketbank.ms_transfer.enums.TransferType;
import bo.cirrus.pocketbank.ms_transfer.services.AccountService;
import bo.cirrus.pocketbank.ms_transfer.validation.TransferValidation;
import bo.cirrus.pocketbank.commons.dto.ResponseDto;
import bo.cirrus.pocketbank.commons.exception.ServiceException;
import bo.cirrus.pocketbank.commons.util.Execute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TransferBl {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferBl.class);

    private final Map<TransferType, Transfer> strategies;
    private final TransferValidation transferValidation;
    private final TransferPersistenceBl transferPersistenceBl;
    private final AccountService accountService;
    
    @Value("${code.eif}")
    private String codeEif;

    // Strategy pattern: inject all Transfer implementations
    public TransferBl(List<Transfer> transferStrategies,
                      TransferValidation transferValidation,
                      TransferPersistenceBl transferPersistenceBl,
                      AccountService accountService) {
        
        this.strategies = transferStrategies.stream()
            .collect(Collectors.toMap(Transfer::type, Function.identity()));
        this.transferValidation = transferValidation;
        this.transferPersistenceBl = transferPersistenceBl;
        this.accountService = accountService;
    }

    public TransferResponseDto requestTransfer(String token, 
                                                String customerId,
                                                TransferRequestDto request) {
        
        LOGGER.info("BL: requestTransfer - customerId: {}, type: {}", 
            customerId, request.getTransferType());
        
        // 1. Parse transfer type
        TransferType type = TransferType.from(request.getTransferType());
        
        // 2. Get strategy for this type
        Transfer strategy = strategies.get(type);
        if (strategy == null) {
            throw new ServiceException(1000, "Tipo de transferencia no soportado: " + type);
        }
        
        // 3. Validate common rules
        transferValidation.validateCurrencyNotBlocked(request.getCurrencyId());
        transferValidation.validateAmountGreaterThanZero(request.getAmount());
        transferValidation.validateAccountsAreDifferent(
            request.getOriginAccountNumber(), 
            request.getTargetAccountNumber()
        );
        
        // 4. Get account information (external service call)
        AccountInformationDto originAccount = getAccountInformation(
            token, customerId, request.getOriginAccountNumber());
        
        // 5. Delegate to specific strategy
        TransferResponseDto response = strategy.requestTransfer(
            token, customerId, request, originAccount);
        
        // 6. Persist transfer record
        TfTransfer tfTransfer = buildTransferEntity(customerId, request, response);
        transferPersistenceBl.saveTransfer(tfTransfer);
        
        LOGGER.info("BL: requestTransfer completed - transferId: {}", 
            response.getTransferId());
        
        return response;
    }

    private AccountInformationDto getAccountInformation(String token, 
                                                         String customerId,
                                                         String accountNumber) {
        LOGGER.info("BL: getAccountInformation - account: {}", accountNumber);
        
        ResponseDto<AccountInformationDto> response = Execute.orFail(
            () -> accountService.getAccountInformation(token, customerId, accountNumber),
            ex -> LOGGER.error("ERROR-BL: getAccountInformation failed", ex)
        );
        
        return response.getData();
    }

    private TfTransfer buildTransferEntity(String customerId,
                                           TransferRequestDto request,
                                           TransferResponseDto response) {
        TfTransfer entity = new TfTransfer();
        entity.setCustomerId(customerId);
        entity.setOriginAccountNumber(request.getOriginAccountNumber());
        entity.setTargetAccountNumber(request.getTargetAccountNumber());
        entity.setAmount(request.getAmount());
        entity.setCurrencyId(request.getCurrencyId());
        entity.setStatus(response.getStatus());
        // Generate business key for watchdog
        entity.setWatchdogBusinessKey(generateBusinessKey(response.getTransferId()));
        return entity;
    }

    private String generateBusinessKey(Integer transferId) {
        // Format: EIF + OperationType + CustomerId(padded) + TransferId(padded)
        // Example: 10343000001230001
        return String.format("%s%d%010d%04d", codeEif, 3, 123, transferId);
    }
}
```

### Strategy Pattern Subdirectory (`bl/transfer/`)

**When to Use:** When an operation has multiple implementations based on type (e.g., OWN/THIRD/OTHER transfers, different payment methods).

#### Interface Definition

```java
package bo.cirrus.pocketbank.ms_transfer.bl.transfer;

import bo.cirrus.pocketbank.ms_transfer.dto.account.AccountInformationDto;
import bo.cirrus.pocketbank.ms_transfer.dto.core.CoreTransferResponseDto;
import bo.cirrus.pocketbank.ms_transfer.dto.transfer.TransferRequestDto;
import bo.cirrus.pocketbank.ms_transfer.dto.transfer.TransferResponseDto;
import bo.cirrus.pocketbank.ms_transfer.enums.TransferType;

public interface Transfer {
    
    /**
     * Returns the transfer type this strategy handles
     */
    TransferType type();
    
    /**
     * Request a new transfer (initiates 2FA if required)
     */
    TransferResponseDto requestTransfer(String token,
                                        String customerId,
                                        TransferRequestDto request,
                                        AccountInformationDto originAccount);
    
    /**
     * Execute the actual transfer after 2FA confirmation
     */
    CoreTransferResponseDto doTransfer(String token,
                                       String customerId,
                                       TransferRequestDto request);
}
```

#### Strategy Implementation

```java
package bo.cirrus.pocketbank.ms_transfer.bl.transfer;

import bo.cirrus.pocketbank.ms_transfer.dto.account.AccountInformationDto;
import bo.cirrus.pocketbank.ms_transfer.dto.core.CoreTransferRequestDto;
import bo.cirrus.pocketbank.ms_transfer.dto.core.CoreTransferResponseDto;
import bo.cirrus.pocketbank.ms_transfer.dto.transfer.TransferRequestDto;
import bo.cirrus.pocketbank.ms_transfer.dto.transfer.TransferResponseDto;
import bo.cirrus.pocketbank.ms_transfer.enums.TransferType;
import bo.cirrus.pocketbank.ms_transfer.enums.TransferStatusEnum;
import bo.cirrus.pocketbank.ms_transfer.services.MfaService;
import bo.cirrus.pocketbank.ms_transfer.services.TransferCoreService;
import bo.cirrus.pocketbank.ms_transfer.validation.TransferValidation;
import bo.cirrus.pocketbank.commons.dto.ResponseDto;
import bo.cirrus.pocketbank.commons.util.Execute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OwnTransfer implements Transfer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OwnTransfer.class);

    private final TransferValidation transferValidation;
    private final MfaService mfaService;
    private final TransferCoreService transferCoreService;

    public OwnTransfer(TransferValidation transferValidation,
                       MfaService mfaService,
                       TransferCoreService transferCoreService) {
        this.transferValidation = transferValidation;
        this.mfaService = mfaService;
        this.transferCoreService = transferCoreService;
    }

    @Override
    public TransferType type() {
        return TransferType.OWN;
    }

    @Override
    public TransferResponseDto requestTransfer(String token,
                                               String customerId,
                                               TransferRequestDto request,
                                               AccountInformationDto originAccount) {
        
        LOGGER.info("BL-OWN: requestTransfer - amount: {}", request.getAmount());
        
        // Own transfer specific validations
        transferValidation.validateCurrencyCompatibility(
            originAccount.getCurrencyId(),
            request.getTargetCurrencyId(),
            request.getCurrencyId()
        );
        
        // Request 2FA
        String mfaToken = requestTwoFactorAuth(token, customerId, request);
        
        // Build response
        TransferResponseDto response = new TransferResponseDto();
        response.setStatus(TransferStatusEnum.TWO_FA_REQUESTED.getCode());
        response.setMessage(TransferStatusEnum.TWO_FA_REQUESTED.getDescription());
        response.setMfaToken(mfaToken);
        
        LOGGER.info("BL-OWN: requestTransfer completed - 2FA requested");
        
        return response;
    }

    @Override
    public CoreTransferResponseDto doTransfer(String token,
                                              String customerId,
                                              TransferRequestDto request) {
        
        LOGGER.info("BL-OWN: doTransfer - executing core transfer");
        
        CoreTransferRequestDto coreRequest = buildCoreRequest(request);
        
        ResponseDto<CoreTransferResponseDto> response = Execute.orFail(
            () -> transferCoreService.executeTransfer(token, coreRequest),
            ex -> LOGGER.error("ERROR-BL-OWN: doTransfer failed", ex)
        );
        
        LOGGER.info("BL-OWN: doTransfer completed");
        
        return response.getData();
    }

    private String requestTwoFactorAuth(String token, String customerId, 
                                        TransferRequestDto request) {
        // MFA service call
        return "mfa-token-placeholder";
    }

    private CoreTransferRequestDto buildCoreRequest(TransferRequestDto request) {
        CoreTransferRequestDto core = new CoreTransferRequestDto();
        core.setOriginAccount(request.getOriginAccountNumber());
        core.setTargetAccount(request.getTargetAccountNumber());
        core.setAmount(request.getAmount());
        core.setCurrency(request.getCurrencyId());
        core.setOperationType(TransferType.OWN.getOperationCode());
        return core;
    }
}
```

### Persistence BL (`{Entity}PersistenceBl.java`)

**When to Use:** When you need isolated transaction boundaries (e.g., saving a record even if subsequent operations fail).

```java
package bo.cirrus.pocketbank.ms_transfer.bl;

import bo.cirrus.pocketbank.ms_transfer.dao.domain.TfTransfer;
import bo.cirrus.pocketbank.ms_transfer.dao.repository.TfTransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferPersistenceBl {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferPersistenceBl.class);

    private final TfTransferRepository tfTransferRepository;

    public TransferPersistenceBl(TfTransferRepository tfTransferRepository) {
        this.tfTransferRepository = tfTransferRepository;
    }

    /**
     * Saves transfer in a new transaction - ensures persistence even if 
     * outer transaction rolls back
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TfTransfer saveTransfer(TfTransfer tfTransfer) {
        LOGGER.info("BL-PERSISTENCE: saveTransfer");
        
        // Get next ID from sequence
        Integer nextId = tfTransferRepository.getNextSequenceValue();
        tfTransfer.setTransferId(nextId);
        
        return tfTransferRepository.save(tfTransfer);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTransferStatus(Integer transferId, String status) {
        LOGGER.info("BL-PERSISTENCE: updateTransferStatus - id: {}, status: {}", 
            transferId, status);
        
        TfTransfer transfer = tfTransferRepository.findById(transferId)
            .orElseThrow(() -> new ServiceException(1002, "Transferencia no encontrada"));
        
        transfer.setStatus(status);
        tfTransferRepository.save(transfer);
    }
}
```

---

## DAO Layer (`dao/`)

### Domain Entities (`dao/domain/`)

**When to Use:** Mapping database tables to Java objects.

#### Naming Conventions
- Class name: `{Prefix}{Entity}.java` (e.g., `TfTransfer`, `BeBeneficiary`, `PbCatalog`)
- Table prefixes take after the package name (e.g., `Tf` for transfer, `Be` for beneficiary, `Pb` for catalog)

#### Complete Example

```java
package bo.cirrus.pocketbank.ms_transfer.dao.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tf_transfer")
public class TfTransfer {

    @Id
    @Column(name = "transfer_id")
    private Integer transferId;

    @Column(name = "customer_id", length = 50)
    private String customerId;

    @Column(name = "origin_account_number", length = 30)
    private String originAccountNumber;

    @Column(name = "target_account_number", length = 30)
    private String targetAccountNumber;

    @Column(name = "amount", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_id")
    private Integer currencyId;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "watchdog_business_key", length = 50)
    private String watchdogBusinessKey;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Integer version;

    // Manual getters and setters (no Lombok)
    
    public Integer getTransferId() {
        return transferId;
    }

    public void setTransferId(Integer transferId) {
        this.transferId = transferId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getOriginAccountNumber() {
        return originAccountNumber;
    }

    public void setOriginAccountNumber(String originAccountNumber) {
        this.originAccountNumber = originAccountNumber;
    }

    public String getTargetAccountNumber() {
        return targetAccountNumber;
    }

    public void setTargetAccountNumber(String targetAccountNumber) {
        this.targetAccountNumber = targetAccountNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getCurrencyId() {
        return currencyId;
    }

    public void setCurrencyId(Integer currencyId) {
        this.currencyId = currencyId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWatchdogBusinessKey() {
        return watchdogBusinessKey;
    }

    public void setWatchdogBusinessKey(String watchdogBusinessKey) {
        this.watchdogBusinessKey = watchdogBusinessKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
```

#### Entity with Relationships

```java
package bo.cirrus.pocketbank.ms_transfer.dao.domain;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "pb_catalog")
public class PbCatalog {

    @Id
    @Column(name = "catalog_id")
    private Integer catalogId;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @OneToMany(mappedBy = "catalog", fetch = FetchType.LAZY)
    private List<PbCatalogOption> options;

    // Getters and setters...
}

@Entity
@Table(name = "pb_catalog_option")
public class PbCatalogOption {

    @Id
    @Column(name = "catalog_option_id")
    private Integer catalogOptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id")
    private PbCatalog catalog;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "value", length = 255)
    private String value;

    // Getters and setters...
}
```

### Repositories (`dao/repository/`)

**When to Use:** All database access operations.

#### Naming Convention
- `{Entity}Repository.java` or `{Entity}Dao.java`

#### Complete Example

```java
package bo.cirrus.pocketbank.ms_transfer.dao.repository;

import bo.cirrus.pocketbank.ms_transfer.dao.domain.TfTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface TfTransferRepository extends JpaRepository<TfTransfer, Integer> {

    // 1. Spring Data method naming (simple queries)
    Optional<TfTransfer> findByWatchdogBusinessKey(String watchdogBusinessKey);
    
    List<TfTransfer> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    // 2. Native SQL for sequences
    @Query(value = "SELECT NEXT VALUE FOR sq_tf_transfer_id", nativeQuery = true)
    Integer getNextSequenceValue();

    // 3. JPQL for complex queries (uses entity/field names)
    @Query("""
        SELECT t FROM TfTransfer t
        WHERE t.customerId = :customerId
        AND t.status = :status
        ORDER BY t.createdAt DESC
    """)
    List<TfTransfer> findByCustomerAndStatus(
        @Param("customerId") String customerId,
        @Param("status") String status
    );

    // 4. Native SQL for complex operations
    @Query(value = """
        SELECT t.* FROM tf_transfer t
        WHERE t.customer_id = :customerId
        AND t.created_at >= DATEADD(day, -30, GETDATE())
        ORDER BY t.created_at DESC
    """, nativeQuery = true)
    List<TfTransfer> findRecentTransfers(@Param("customerId") String customerId);

    // 5. Modifying queries (INSERT/UPDATE/DELETE)
    @Transactional
    @Modifying
    @Query("""
        UPDATE TfTransfer t
        SET t.status = :newStatus, t.updatedAt = CURRENT_TIMESTAMP
        WHERE t.transferId = :transferId
    """)
    int updateStatus(
        @Param("transferId") Integer transferId,
        @Param("newStatus") String newStatus
    );
}
```

---

## DTO Layer (`dto/`)

### When to Use
- API request/response payloads
- Inter-service communication
- Data transformation between layers

### Directory Structure Example

```
dto/
├── ListProductsDto.java           # Standalone DTOs
├── MiddlewareResponse.java        # Generic wrapper
├── account/                       # Account domain
│   ├── AccountInformationDto.java
│   └── AccountInformationResponseDto.java
├── beneficiary/                   # Beneficiary domain
├── core/                          # Core banking DTOs
│   ├── CoreTransferRequestDto.java
│   └── CoreTransferResponseDto.java
├── customer/                      # Customer domain
├── feign/                         # External service DTOs
│   └── GrantedAccountDto.java
├── transfer/                      # Transfer domain
│   ├── TransferRequestDto.java
│   ├── TransferResponseDto.java
│   └── ConfirmTransferRequestDto.java
└── TwoFactor/                     # 2FA related
```

### Request DTO Example

```java
package bo.cirrus.pocketbank.ms_transfer.dto.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serializable;
import java.math.BigDecimal;

public class TransferRequestDto implements Serializable {

    @NotBlank(message = "Transfer type is required")
    private String transferType;

    @NotBlank(message = "Origin account is required")
    private String originAccountNumber;

    @NotBlank(message = "Target account is required")
    private String targetAccountNumber;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private Integer currencyId;

    private String description;
    
    private String sourceOfFundsDescription;
    
    private String purposeDescription;

    // No-arg constructor
    public TransferRequestDto() {
    }

    // All-args constructor
    public TransferRequestDto(String transferType, String originAccountNumber,
                              String targetAccountNumber, BigDecimal amount,
                              Integer currencyId, String description) {
        this.transferType = transferType;
        this.originAccountNumber = originAccountNumber;
        this.targetAccountNumber = targetAccountNumber;
        this.amount = amount;
        this.currencyId = currencyId;
        this.description = description;
    }

    // Getters and setters
    public String getTransferType() { return transferType; }
    public void setTransferType(String transferType) { this.transferType = transferType; }

    public String getOriginAccountNumber() { return originAccountNumber; }
    public void setOriginAccountNumber(String originAccountNumber) { 
        this.originAccountNumber = originAccountNumber; 
    }

    public String getTargetAccountNumber() { return targetAccountNumber; }
    public void setTargetAccountNumber(String targetAccountNumber) { 
        this.targetAccountNumber = targetAccountNumber; 
    }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Integer getCurrencyId() { return currencyId; }
    public void setCurrencyId(Integer currencyId) { this.currencyId = currencyId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // Manual toString for logging
    @Override
    public String toString() {
        return "TransferRequestDto{" +
            "transferType='" + transferType + '\'' +
            ", originAccountNumber='" + originAccountNumber + '\'' +
            ", targetAccountNumber='" + targetAccountNumber + '\'' +
            ", amount=" + amount +
            ", currencyId=" + currencyId +
            '}';
    }
}
```

### Response DTO Example

```java
package bo.cirrus.pocketbank.ms_transfer.dto.transfer;

import java.io.Serializable;
import java.math.BigDecimal;

public class TransferResponseDto implements Serializable {

    private Integer transferId;
    private String status;
    private String message;
    private String mfaToken;
    private BigDecimal fee;
    private String transactionReference;

    public TransferResponseDto() {
    }

    // Getters and setters...

    @Override
    public String toString() {
        return "TransferResponseDto{" +
            "transferId=" + transferId +
            ", status='" + status + '\'' +
            ", message='" + message + '\'' +
            '}';
    }
}
```

### Generic Response Wrapper

```java
package bo.cirrus.pocketbank.ms_transfer.dto;

/**
 * Generic wrapper for middleware service responses
 */
public class MiddlewareResponse<T> {

    private String statusCode;
    private T response;
    private String errorDetail;
    private String httpStatus;
    private String rootCause;
    private Boolean validStatusCode;

    public boolean isSuccess() {
        return Boolean.TRUE.equals(validStatusCode) && "200".equals(statusCode);
    }

    // Getters and setters...
}
```

---

## Services Layer (`services/`)

### When to Use
Calling external microservices via HTTP.

### Pattern
Uses `@HttpExchange` (Spring Boot 4.x native HTTP clients, replacing Feign).

### Complete Example

```java
package bo.cirrus.pocketbank.ms_transfer.services;

import bo.cirrus.pocketbank.ms_transfer.dto.MiddlewareResponse;
import bo.cirrus.pocketbank.ms_transfer.dto.account.AccountInformationDto;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface AccountService {

    @GetExchange("/v1/customer/account/information")
    MiddlewareResponse<AccountInformationDto> getAccountInformation(
        @RequestHeader("Authorization") String token,
        @RequestHeader("x-Customer-id") String customerId,
        @RequestParam("accountNumber") String accountNumber
    );

    @GetExchange("/v1/customer/account/balance")
    MiddlewareResponse<AccountBalanceDto> getAccountBalance(
        @RequestHeader("Authorization") String token,
        @RequestHeader("x-Customer-id") String customerId,
        @RequestParam("accountNumber") String accountNumber
    );
}
```

```java
package bo.cirrus.pocketbank.ms_transfer.services;

import bo.cirrus.pocketbank.ms_transfer.dto.core.CoreTransferRequestDto;
import bo.cirrus.pocketbank.ms_transfer.dto.core.CoreTransferResponseDto;
import bo.cirrus.pocketbank.commons.dto.ResponseDto;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface TransferCoreService {

    @PostExchange("/v1/customer/transfer")
    ResponseDto<CoreTransferResponseDto> executeTransfer(
        @RequestHeader("Authorization") String token,
        @RequestBody CoreTransferRequestDto request
    );

    @PostExchange("/v1/customer/transfer/reverse")
    ResponseDto<CoreTransferResponseDto> reverseTransfer(
        @RequestHeader("Authorization") String token,
        @RequestBody CoreReverseRequestDto request
    );
}
```

---

## Config Layer (`config/`)

### When to Use
- Wiring Spring beans
- Security configuration
- HTTP client configuration
- Component scanning

### HTTP Client Configuration

```java
package bo.cirrus.pocketbank.ms_transfer.config;

import bo.cirrus.pocketbank.ms_transfer.services.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class HttpClientConfig {

    @Value("${feign.account.url}")
    private String accountBaseUrl;

    @Value("${feign.core.url}")
    private String coreBaseUrl;

    @Value("${feign.mfa.url}")
    private String mfaBaseUrl;

    @Bean
    public AccountService accountService() {
        return createClient(accountBaseUrl, AccountService.class);
    }

    @Bean
    public TransferCoreService transferCoreService() {
        return createClient(coreBaseUrl, TransferCoreService.class);
    }

    @Bean
    public MfaService mfaService() {
        return createClient(mfaBaseUrl, MfaService.class);
    }

    private <T> T createClient(String baseUrl, Class<T> serviceClass) {
        RestClient restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build();
        
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build();
        
        return factory.createClient(serviceClass);
    }
}
```

### Security Configuration (OAuth2 + Keycloak)

```java
package bo.cirrus.pocketbank.ms_transfer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().permitAll()  // Adjust based on requirements
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .csrf(csrf -> csrf.disable());
        
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    /**
     * Extracts roles from Keycloak's realm_access.roles claim
     */
    private static class KeycloakRealmRoleConverter 
            implements Converter<Jwt, Collection<GrantedAuthority>> {
        
        @Override
        @SuppressWarnings("unchecked")
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null || !realmAccess.containsKey("roles")) {
                return List.of();
            }
            
            List<String> roles = (List<String>) realmAccess.get("roles");
            return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
        }
    }
}
```

---

## Validation Layer (`validation/`)

### When to Use
Reusable business rule validation with configurable thresholds from `application.yaml`.

### Complete Example

```java
package bo.cirrus.pocketbank.ms_transfer.validation;

import bo.cirrus.pocketbank.commons.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransferValidation {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferValidation.class);

    @Value("${transfer.block.currency.usd.enabled}")
    private boolean usdBlocked;

    @Value("${transfer.block.currency.usd.message}")
    private String usdBlockedMessage;

    @Value("${transfer.validation.description.min-length}")
    private int descriptionMinLength;

    @Value("${transfer.validation.description.amount-threshold-bob}")
    private BigDecimal amountThresholdBob;

    @Value("${transfer.validation.description.amount-threshold-usd}")
    private BigDecimal amountThresholdUsd;

    public void validateCurrencyNotBlocked(Integer currencyId) {
        LOGGER.info("VALIDATION: validateCurrencyNotBlocked - currencyId: {}", currencyId);
        
        if (currencyId == 2 && usdBlocked) {
            throw new ServiceException(1010, usdBlockedMessage);
        }
    }

    public void validateAmountGreaterThanZero(BigDecimal amount) {
        LOGGER.info("VALIDATION: validateAmountGreaterThanZero - amount: {}", amount);
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(1011, "El monto debe ser mayor a cero");
        }
    }

    public void validateAmountNotNegative(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException(1012, "El monto no puede ser negativo");
        }
    }

    public void validateAccountsAreDifferent(String originAccount, String targetAccount) {
        LOGGER.info("VALIDATION: validateAccountsAreDifferent");
        
        if (originAccount != null && originAccount.equals(targetAccount)) {
            throw new ServiceException(1013, 
                "La cuenta de origen y destino no pueden ser la misma");
        }
    }

    public void validateDescriptionMinLength(BigDecimal amount, 
                                              Integer currencyId, 
                                              String description) {
        LOGGER.info("VALIDATION: validateDescriptionMinLength");
        
        BigDecimal threshold = (currencyId == 1) ? amountThresholdBob : amountThresholdUsd;
        
        if (amount.compareTo(threshold) >= 0) {
            if (description == null || description.length() < descriptionMinLength) {
                throw new ServiceException(1014, 
                    "La descripción debe tener al menos " + descriptionMinLength + 
                    " caracteres para montos mayores a " + threshold);
            }
        }
    }

    public void validateCurrencyCompatibility(Integer originCurrencyId,
                                               Integer targetCurrencyId,
                                               Integer transactionCurrencyId) {
        LOGGER.info("VALIDATION: validateCurrencyCompatibility");
        
        // Same currency transfers only
        if (!originCurrencyId.equals(targetCurrencyId)) {
            throw new ServiceException(1015, 
                "Las cuentas deben tener la misma moneda");
        }
        
        if (!originCurrencyId.equals(transactionCurrencyId)) {
            throw new ServiceException(1016, 
                "La moneda de la transacción debe coincidir con las cuentas");
        }
    }
}
```

---

## Util Layer (`util/`)

### When to Use
- **Static utility classes:** Stateless helper functions
- **Spring-managed mappers:** When dependencies are needed

### Static Utility Example

```java
package bo.cirrus.pocketbank.ms_transfer.util;

/**
 * Currency utilities - stateless, no dependencies
 */
public final class CurrencyUtil {

    // Private constructor prevents instantiation
    private CurrencyUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String toDescription(Integer currencyId) {
        return switch (currencyId) {
            case 1 -> "Bs.";
            case 2 -> "Usd.";
            default -> "Desconocido";
        };
    }

    public static String currencyISO4217(Integer currencyId) {
        return switch (currencyId) {
            case 1 -> "BOB";
            case 2 -> "USD";
            default -> "BOB";
        };
    }

    public static Integer fromISO4217(String iso) {
        return switch (iso.toUpperCase()) {
            case "BOB" -> 1;
            case "USD" -> 2;
            default -> 1;
        };
    }
}
```

### Spring-Managed Mapper Example

```java
package bo.cirrus.pocketbank.ms_transfer.util;

import bo.cirrus.pocketbank.ms_transfer.dto.account.AccountInformationDto;
import bo.cirrus.pocketbank.ms_transfer.dto.account.AccountInformationResponseDto;
import org.springframework.stereotype.Component;

/**
 * Account mapper - Spring-managed for potential dependency injection
 */
@Component
public class AccountInformationMapper {

    public AccountInformationResponseDto mapToResponse(AccountInformationDto source,
                                                        String customerId) {
        AccountInformationResponseDto response = new AccountInformationResponseDto();
        response.setAccountNumber(source.getAccountNumber());
        response.setAccountType(source.getAccountType());
        response.setCurrency(CurrencyUtil.toDescription(source.getCurrencyId()));
        response.setBalance(source.getAvailableBalance());
        response.setCustomerId(customerId);
        return response;
    }
}
```

---

## Enums Layer (`enums/`)

### When to Use
Fixed value sets with associated data or behavior.

### Complete Examples

```java
package bo.cirrus.pocketbank.ms_transfer.enums;

public enum TransferType {
    OWN(3),
    THIRD(4),
    OTHER(5);

    private final int operationCode;

    TransferType(int operationCode) {
        this.operationCode = operationCode;
    }

    public int getOperationCode() {
        return operationCode;
    }

    /**
     * Factory method - parses from string input
     */
    public static TransferType from(String raw) {
        return switch (raw.trim().toUpperCase()) {
            case "OWN", "PROPIA" -> OWN;
            case "THIRD", "TERCEROS" -> THIRD;
            case "OTHER", "ACH" -> OTHER;
            default -> throw new IllegalArgumentException(
                "Unsupported transferType: " + raw);
        };
    }
}
```

```java
package bo.cirrus.pocketbank.ms_transfer.enums;

public enum TransferStatusEnum {
    TWO_FA_REQUESTED("2FA_REQUESTED", "Se ha iniciado la transferencia"),
    TWO_FA_CONFIRMED("2FA_CONFIRMED", "Código verificado correctamente"),
    SUCCESSFUL("SUCCESSFUL", "Transferencia realizada exitosamente"),
    ERROR("ERROR", "Error en la transferencia"),
    ERROR_INVOKE("ERROR_INVOKE", "Error al invocar servicio externo");

    private final String code;
    private final String description;

    TransferStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static TransferStatusEnum fromCode(String code) {
        for (TransferStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status code: " + code);
    }
}
```

```java
package bo.cirrus.pocketbank.ms_transfer.enums;

public enum CurrencyEnum {
    BS(1, "Bs.", "BOB"),
    USD(2, "Usd.", "USD");

    private final int id;
    private final String symbol;
    private final String iso4217;

    CurrencyEnum(int id, String symbol, String iso4217) {
        this.id = id;
        this.symbol = symbol;
        this.iso4217 = iso4217;
    }

    public int getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getIso4217() { return iso4217; }

    public static CurrencyEnum fromId(Integer id) {
        for (CurrencyEnum currency : values()) {
            if (currency.id == id) {
                return currency;
            }
        }
        return BS; // Default
    }
}
```
