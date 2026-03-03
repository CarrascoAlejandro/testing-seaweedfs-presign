---
applyTo: '**'
description: This skill documents the implementation patterns and conventions used across Cirrus/ProMujer microservices.
---
# Coding Patterns

This skill documents the implementation patterns and conventions used across Cirrus/ProMujer microservices.

## When to Use

Reference this skill when:
- Implementing a new feature
- Writing new code in any layer
- Reviewing code for pattern compliance
- Debugging issues related to logging, exceptions, or transactions

---

## Controller Pattern

### When to Use
Every REST endpoint follows this pattern for consistency and proper request context handling.

### Pattern Steps
1. Log entry with `API:` prefix including key parameters
2. Create `RequestParamDto` from HttpServletRequest
3. Extract token and customerId from request context
4. Call business logic method
5. Wrap result in `ResponseDto`
6. Return `ResponseEntity.ok()`

### Complete Example

```java
@PostMapping("/request")
public ResponseEntity<ResponseDto<TransferResponseDto>> requestTransfer(
        HttpServletRequest request,
        @RequestBody TransferRequestDto transferRequestDto) {
    
    // 1. Log entry
    LOGGER.info("API: requestTransfer - type: {}, amount: {}", 
        transferRequestDto.getTransferType(),
        transferRequestDto.getAmount());
    
    // 2. Create request context
    RequestParamDto requestParamDto = new RequestParamDto(request, tracer);
    
    // 3. Extract authentication context
    String token = requestParamDto.getToken();
    String customerId = requestParamDto.getCustomerId();
    
    // 4. Call business logic
    TransferResponseDto response = transferBl.requestTransfer(
        token, 
        customerId, 
        transferRequestDto
    );
    
    // 5-6. Wrap and return
    return ResponseEntity.ok(new ResponseDto<>(response));
}
```

### With Query Parameters

```java
@GetMapping("/history")
public ResponseEntity<ResponseDto<List<TransferHistoryDto>>> getTransferHistory(
        HttpServletRequest request,
        @RequestParam("accountNumber") String accountNumber,
        @RequestParam(value = "limit", defaultValue = "10") Integer limit,
        @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
    
    LOGGER.info("API: getTransferHistory - account: {}, limit: {}, offset: {}", 
        accountNumber, limit, offset);
    
    RequestParamDto requestParamDto = new RequestParamDto(request, tracer);
    
    List<TransferHistoryDto> history = transferBl.getTransferHistory(
        requestParamDto.getToken(),
        requestParamDto.getCustomerId(),
        accountNumber,
        limit,
        offset
    );
    
    return ResponseEntity.ok(new ResponseDto<>(history));
}
```

### With Path Variables

```java
@GetMapping("/{transferId}")
public ResponseEntity<ResponseDto<TransferDetailDto>> getTransferDetail(
        HttpServletRequest request,
        @PathVariable("transferId") Integer transferId) {
    
    LOGGER.info("API: getTransferDetail - transferId: {}", transferId);
    
    RequestParamDto requestParamDto = new RequestParamDto(request, tracer);
    
    TransferDetailDto detail = transferBl.getTransferDetail(
        requestParamDto.getToken(),
        requestParamDto.getCustomerId(),
        transferId
    );
    
    return ResponseEntity.ok(new ResponseDto<>(detail));
}
```

---

## Strategy Pattern

### When to Use
When an operation has multiple implementations based on type:
- Different transfer types (OWN, THIRD, OTHER)
- Different payment methods
- Different notification channels
- Any polymorphic behavior driven by a type discriminator

### Pattern Structure

```
bl/
├── FeatureBl.java              # Orchestrator with strategy map
└── feature/                     # Strategy subdirectory
    ├── Feature.java            # Interface
    ├── FeatureTypeA.java       # Implementation A
    ├── FeatureTypeB.java       # Implementation B
    └── FeatureTypeC.java       # Implementation C
```

### Step 1: Define the Interface

```java
package bo.cirrus.pocketbank.ms_transfer.bl.transfer;

public interface Transfer {
    
    /**
     * Identifies which type this strategy handles.
     * Used to build the strategy map.
     */
    TransferType type();
    
    /**
     * Main operation method - signature matches across all implementations
     */
    TransferResponseDto requestTransfer(String token,
                                        String customerId,
                                        TransferRequestDto request,
                                        AccountInformationDto originAccount);
    
    /**
     * Additional operation methods as needed
     */
    CoreTransferResponseDto doTransfer(String token,
                                       String customerId,
                                       TransferRequestDto request);
}
```

### Step 2: Implement Each Strategy

```java
package bo.cirrus.pocketbank.ms_transfer.bl.transfer;

@Component  // Essential: makes it discoverable by Spring
public class OwnTransfer implements Transfer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OwnTransfer.class);

    // Inject dependencies specific to this strategy
    private final TransferValidation transferValidation;
    private final MfaService mfaService;

    public OwnTransfer(TransferValidation transferValidation, MfaService mfaService) {
        this.transferValidation = transferValidation;
        this.mfaService = mfaService;
    }

    @Override
    public TransferType type() {
        return TransferType.OWN;  // Unique identifier
    }

    @Override
    public TransferResponseDto requestTransfer(String token,
                                               String customerId,
                                               TransferRequestDto request,
                                               AccountInformationDto originAccount) {
        
        LOGGER.info("BL-OWN: requestTransfer");  // Strategy-specific log prefix
        
        // OWN-specific validations
        transferValidation.validateCurrencyCompatibility(
            originAccount.getCurrencyId(),
            request.getTargetCurrencyId(),
            request.getCurrencyId()
        );
        
        // OWN-specific logic
        // ... implementation details ...
        
        return response;
    }

    @Override
    public CoreTransferResponseDto doTransfer(String token,
                                              String customerId,
                                              TransferRequestDto request) {
        LOGGER.info("BL-OWN: doTransfer");
        // ... implementation ...
    }
}
```

```java
@Component
public class ThirdTransfer implements Transfer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThirdTransfer.class);

    @Override
    public TransferType type() {
        return TransferType.THIRD;
    }

    @Override
    public TransferResponseDto requestTransfer(...) {
        LOGGER.info("BL-THIRD: requestTransfer");
        
        // THIRD-specific validations (e.g., beneficiary validation)
        // ... THIRD-specific logic ...
    }
}
```

### Step 3: Build Strategy Map in Orchestrator

```java
package bo.cirrus.pocketbank.ms_transfer.bl;

@Service
public class TransferBl {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferBl.class);
    
    private final Map<TransferType, Transfer> strategies;

    /**
     * Spring injects ALL Transfer implementations as a List.
     * We convert to Map for O(1) lookup by type.
     */
    public TransferBl(List<Transfer> transferStrategies,
                      /* other dependencies */) {
        
        this.strategies = transferStrategies.stream()
            .collect(Collectors.toMap(
                Transfer::type,        // Key: the type() return value
                Function.identity()    // Value: the strategy itself
            ));
        
        LOGGER.info("BL: Loaded {} transfer strategies: {}", 
            strategies.size(), strategies.keySet());
    }

    public TransferResponseDto requestTransfer(String token,
                                               String customerId,
                                               TransferRequestDto request) {
        
        LOGGER.info("BL: requestTransfer - type: {}", request.getTransferType());
        
        // 1. Parse type from request
        TransferType type = TransferType.from(request.getTransferType());
        
        // 2. Get strategy (fails fast if unknown type)
        Transfer strategy = strategies.get(type);
        if (strategy == null) {
            throw new ServiceException(1000, 
                "Tipo de transferencia no soportado: " + type);
        }
        
        // 3. Common validations (before delegation)
        // ...
        
        // 4. Delegate to strategy
        return strategy.requestTransfer(token, customerId, request, originAccount);
    }
}
```

---

## Service Exception Pattern

### When to Use
- Business rule violations
- Validation failures
- Expected error conditions
- Any error that should be communicated to the client

**Do NOT use for:**
- Unexpected system errors (let them propagate)
- IO/Network errors (use Execute.orFail wrapper)

### Pattern

```java
// Basic usage
throw new ServiceException(errorCode, message);

// Examples
throw new ServiceException(1000, "Tipo de transferencia no soportado: " + type);
throw new ServiceException(1001, "El monto debe ser mayor a cero");
throw new ServiceException(1002, "Cuenta de origen no encontrada");
throw new ServiceException(1010, "Las transacciones en dólares están bloqueadas");
```

### Error Code Conventions Example

Error codes should be structured to allow categorization and easy identification of the error type. Here's an example convention:

| Range | Domain |
|-------|--------|
| 1000-1099 | Transfer errors |
| 1100-1199 | Beneficiary errors |
| 1200-1299 | Account errors |
| 1300-1399 | Authentication/Authorization errors |
| 1400-1499 | Validation errors (generic) |
| 1500-1599 | External service errors |

### Validation Helper Pattern

```java
@Component
public class TransferValidation {

    public void validateAmountGreaterThanZero(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(1001, "El monto debe ser mayor a cero");
        }
    }

    public void validateAccountExists(AccountInformationDto account) {
        if (account == null) {
            throw new ServiceException(1002, "Cuenta no encontrada");
        }
    }
}

// Usage in BL
transferValidation.validateAmountGreaterThanZero(request.getAmount());
transferValidation.validateAccountExists(originAccount);
// If we reach here, validations passed
```

---

## External Call Pattern

### When to Use
All external microservice calls where failures should be logged and properly handled.

### Pattern: `Execute.orFail()`

```java
ResponseDto<CoreTransferResponseDto> response = Execute.orFail(
    () -> transferCoreService.executeTransfer(token, coreRequest),  // Supplier
    ex -> LOGGER.error("ERROR-SERVICE: executeTransfer failed - {}", ex.getMessage(), ex)  // Error handler
);
```

### Complete Example

```java
private AccountInformationDto getAccountInformation(String token, 
                                                     String customerId,
                                                     String accountNumber) {
    LOGGER.info("SERVICE: getAccountInformation - account: {}", accountNumber);
    
    MiddlewareResponse<AccountInformationDto> response = Execute.orFail(
        () -> accountService.getAccountInformation(token, customerId, accountNumber),
        ex -> LOGGER.error("ERROR-SERVICE: getAccountInformation failed for account {}", 
            accountNumber, ex)
    );
    
    // Validate response
    if (!response.isSuccess()) {
        LOGGER.error("ERROR-SERVICE: getAccountInformation returned error - {}", 
            response.getErrorDetail());
        throw new ServiceException(1200, 
            "Error al obtener información de cuenta: " + response.getErrorDetail());
    }
    
    LOGGER.info("SUCCESS-SERVICE: getAccountInformation completed");
    return response.getResponse();
}
```

### Multiple Service Calls

```java
public TransferResponseDto processTransfer(String token, 
                                           String customerId,
                                           TransferRequestDto request) {
    
    // Call 1: Get origin account
    AccountInformationDto originAccount = Execute.orFail(
        () -> accountService.getAccountInformation(token, customerId, 
            request.getOriginAccountNumber()),
        ex -> LOGGER.error("ERROR-SERVICE: failed to get origin account", ex)
    ).getResponse();
    
    // Call 2: Validate MFA
    MfaValidationDto mfaResult = Execute.orFail(
        () -> mfaService.validateCode(token, customerId, request.getMfaCode()),
        ex -> LOGGER.error("ERROR-SERVICE: MFA validation failed", ex)
    ).getResponse();
    
    // Call 3: Execute transfer
    CoreTransferResponseDto coreResponse = Execute.orFail(
        () -> transferCoreService.executeTransfer(token, buildCoreRequest(request)),
        ex -> LOGGER.error("ERROR-SERVICE: core transfer failed", ex)
    ).getData();
    
    return mapToResponse(coreResponse);
}
```

---

## Logging Pattern

### When to Use
Always. Every significant operation should be logged with the appropriate prefix.

### Log Prefixes by Layer

| Prefix | Layer | Usage |
|--------|-------|-------|
| `API:` | Controller | Entry point logging |
| `BL:` | Business Logic | General BL operations |
| `BL-{STRATEGY}:` | Strategy | Strategy-specific operations (e.g., `BL-OWN:`, `BL-THIRD:`) |
| `SERVICE:` | External calls | Before calling external service |
| `SUCCESS-SERVICE:` | External calls | After successful external call |
| `ERROR-SERVICE:` | External calls | When external call fails |
| `ERROR-BL:` | Business Logic | Business logic errors |
| `ERROR-{STRATEGY}:` | Strategy | Strategy-specific errors (e.g., `ERROR-OWN:`) |
| `VALIDATION:` | Validation | Validation operations |
| `BL-PERSISTENCE:` | Persistence BL | Database operations |

### Examples

```java
// Controller layer
LOGGER.info("API: requestTransfer - type: {}, amount: {}", type, amount);

// Business logic layer
LOGGER.info("BL: requestTransfer - customerId: {}", customerId);
LOGGER.info("BL: Transfer strategies loaded: {}", strategies.keySet());

// Strategy layer
LOGGER.info("BL-OWN: requestTransfer - validating currency compatibility");
LOGGER.info("BL-THIRD: requestTransfer - checking beneficiary");
LOGGER.info("BL-OTHER: requestTransfer - ACH transfer initiated");

// Service calls
LOGGER.info("SERVICE: getAccountInformation - account: {}", accountNumber);
LOGGER.info("SUCCESS-SERVICE: getAccountInformation completed");
LOGGER.error("ERROR-SERVICE: getAccountInformation failed", exception);

// Validation
LOGGER.info("VALIDATION: validateAmountGreaterThanZero - amount: {}", amount);

// Persistence
LOGGER.info("BL-PERSISTENCE: saveTransfer - id: {}", transferId);

// Errors
LOGGER.error("ERROR-BL: Unexpected error processing transfer", exception);
LOGGER.error("ERROR-OWN: Currency mismatch detected");
```

### What to Log

```java
// DO: Log meaningful context
LOGGER.info("API: requestTransfer - type: {}, originAccount: {}, amount: {}", 
    request.getTransferType(), 
    request.getOriginAccountNumber(), 
    request.getAmount());

// DON'T: Log sensitive data
// LOGGER.info("API: request - token: {}", token);  // Never log tokens
// LOGGER.info("BL: customer data: {}", customerDto);  // May contain PII

// DO: Log operation results
LOGGER.info("BL: Transfer completed - id: {}, status: {}", 
    response.getTransferId(), response.getStatus());

// DO: Log errors with full context
LOGGER.error("ERROR-BL: Failed to process transfer - customerId: {}, type: {}", 
    customerId, transferType, exception);
```

---

## Transaction Pattern

### When to Use
- Critical persistence that must succeed independently
- Operations that should commit even if outer transaction rolls back
- Audit/logging that must persist regardless of business outcome

### Pattern: `REQUIRES_NEW` Propagation

```java
@Service
public class TransferPersistenceBl {

    private final TfTransferRepository tfTransferRepository;

    public TransferPersistenceBl(TfTransferRepository tfTransferRepository) {
        this.tfTransferRepository = tfTransferRepository;
    }

    /**
     * Saves transfer in NEW transaction.
     * Commits independently of caller's transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TfTransfer saveTransfer(TfTransfer tfTransfer) {
        LOGGER.info("BL-PERSISTENCE: saveTransfer");
        
        Integer nextId = tfTransferRepository.getNextSequenceValue();
        tfTransfer.setTransferId(nextId);
        tfTransfer.setCreatedAt(LocalDateTime.now());
        
        return tfTransferRepository.save(tfTransfer);
    }

    /**
     * Updates status in NEW transaction.
     * Used for status tracking even when main operation fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateTransferStatus(Integer transferId, String status) {
        LOGGER.info("BL-PERSISTENCE: updateTransferStatus - id: {}, status: {}", 
            transferId, status);
        
        TfTransfer transfer = tfTransferRepository.findById(transferId)
            .orElseThrow(() -> new ServiceException(1002, "Transfer not found"));
        
        transfer.setStatus(status);
        transfer.setUpdatedAt(LocalDateTime.now());
        
        tfTransferRepository.save(transfer);
    }
}
```

### Usage Pattern

```java
@Service
public class TransferBl {

    private final TransferPersistenceBl transferPersistenceBl;

    public TransferResponseDto processTransfer(...) {
        
        // 1. Save initial record (commits immediately)
        TfTransfer transfer = buildTransferEntity(request);
        transfer.setStatus(TransferStatusEnum.TWO_FA_REQUESTED.getCode());
        transfer = transferPersistenceBl.saveTransfer(transfer);
        
        try {
            // 2. Business operations that might fail
            CoreTransferResponseDto coreResponse = executeCorTransfer(token, request);
            
            // 3. Update status on success
            transferPersistenceBl.updateTransferStatus(
                transfer.getTransferId(), 
                TransferStatusEnum.SUCCESSFUL.getCode()
            );
            
            return mapResponse(coreResponse);
            
        } catch (Exception ex) {
            // 4. Update status on failure (still commits!)
            transferPersistenceBl.updateTransferStatus(
                transfer.getTransferId(), 
                TransferStatusEnum.ERROR.getCode()
            );
            throw ex;
        }
    }
}
```

---

## Business Key Generation

### When to Use
Creating unique identifiers for external systems (watchdog, audit trails, reconciliation).

### Formula

```
BusinessKey = EIF + OperationType + CustomerId(padded) + TransferId(padded)
```

| Component | Description | Length | Example |
|-----------|-------------|--------|---------|
| EIF | Financial entity identifier | 4 | `1034` |
| OperationType | Type code (from TransferType) | 1 | `3` (OWN) |
| CustomerId | Customer ID, zero-padded | 10 | `0000001234` |
| TransferId | Transfer ID, zero-padded | 4 | `0001` |

**Result:** `1034300000012340001`

### Implementation

```java
@Service
public class TransferBl {

    @Value("${code.eif}")
    private String codeEif;

    private String generateBusinessKey(String customerId, 
                                        TransferType type,
                                        Integer transferId) {
        // Pad customer ID to 10 digits
        String paddedCustomerId = String.format("%010d", 
            Long.parseLong(customerId));
        
        // Pad transfer ID to 4 digits
        String paddedTransferId = String.format("%04d", transferId);
        
        // Combine: EIF + OperationType + CustomerId + TransferId
        return codeEif + type.getOperationCode() + paddedCustomerId + paddedTransferId;
    }
}
```

### Complete Usage

```java
public TransferResponseDto requestTransfer(String token, 
                                           String customerId,
                                           TransferRequestDto request) {
    
    // ... validation and processing ...
    
    // Generate business key before saving
    String businessKey = generateBusinessKey(
        customerId, 
        TransferType.from(request.getTransferType()),
        nextTransferId
    );
    
    TfTransfer entity = new TfTransfer();
    entity.setTransferId(nextTransferId);
    entity.setCustomerId(customerId);
    entity.setWatchdogBusinessKey(businessKey);  // For external tracking
    // ... other fields ...
    
    transferPersistenceBl.saveTransfer(entity);
    
    // Include in response for client reference
    TransferResponseDto response = new TransferResponseDto();
    response.setTransactionReference(businessKey);
    
    return response;
}
```

---

## Validation Component Pattern

### When to Use
Business rule validation with:
- Configurable thresholds from `application.yaml`
- Reusable validation methods across multiple BL classes
- Clear error messages for each validation failure

### Configuration (`application.yaml`)

```yaml
transfer:
  block:
    currency:
      usd:
        enabled: false
        message: "Las transacciones en dólares están bloqueadas temporalmente"
  validation:
    description:
      min-length: 10
      amount-threshold-bob: 50000
      amount-threshold-usd: 10000
    pcc:
      amount-threshold-bob: 50000
      amount-threshold-usd: 10000
```

### Validation Component

```java
@Component
public class TransferValidation {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferValidation.class);

    // Inject configuration values
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

    /**
     * Validates currency is not blocked.
     * Configuration-driven - can be toggled without code changes.
     */
    public void validateCurrencyNotBlocked(Integer currencyId) {
        LOGGER.info("VALIDATION: validateCurrencyNotBlocked - currency: {}", currencyId);
        
        if (currencyId == 2 && usdBlocked) {  // 2 = USD
            throw new ServiceException(1010, usdBlockedMessage);
        }
    }

    /**
     * Validates description meets minimum length for large amounts.
     * Threshold varies by currency.
     */
    public void validateDescriptionMinLength(BigDecimal amount,
                                              Integer currencyId,
                                              String description) {
        LOGGER.info("VALIDATION: validateDescriptionMinLength - amount: {}, currency: {}", 
            amount, currencyId);
        
        BigDecimal threshold = (currencyId == 1) ? amountThresholdBob : amountThresholdUsd;
        
        if (amount.compareTo(threshold) >= 0) {
            if (description == null || description.trim().length() < descriptionMinLength) {
                throw new ServiceException(1014, String.format(
                    "Para montos mayores o iguales a %s, la descripción debe tener " +
                    "al menos %d caracteres",
                    threshold.toPlainString(),
                    descriptionMinLength
                ));
            }
        }
    }

    /**
     * Standard validation - no configuration needed.
     */
    public void validateAmountGreaterThanZero(BigDecimal amount) {
        LOGGER.info("VALIDATION: validateAmountGreaterThanZero");
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(1011, "El monto debe ser mayor a cero");
        }
    }
}
```

### Usage in Business Logic

```java
@Service
public class TransferBl {

    private final TransferValidation transferValidation;

    public TransferResponseDto requestTransfer(String token,
                                               String customerId,
                                               TransferRequestDto request) {
        
        LOGGER.info("BL: requestTransfer - running validations");
        
        // Run all validations (fail-fast on first error)
        transferValidation.validateCurrencyNotBlocked(request.getCurrencyId());
        transferValidation.validateAmountGreaterThanZero(request.getAmount());
        transferValidation.validateDescriptionMinLength(
            request.getAmount(),
            request.getCurrencyId(),
            request.getDescription()
        );
        
        LOGGER.info("BL: All validations passed");
        
        // Continue with business logic...
    }
}
```

---

## Constructor Injection Pattern

### When to Use
Always. Prefer constructor injection over `@Autowired` on fields.

### Pattern

```java
@Service
public class TransferBl {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferBl.class);

    // Declare dependencies as final
    private final TransferValidation transferValidation;
    private final TransferPersistenceBl transferPersistenceBl;
    private final AccountService accountService;
    private final Map<TransferType, Transfer> strategies;

    // Single constructor - Spring autowires automatically
    // No @Autowired annotation needed (implicit since Spring 4.3)
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

    // Business methods...
}
```

### Benefits
- Immutable dependencies (`final` fields)
- Clear dependency declaration
- Easier unit testing (mock via constructor)
- Fail-fast if dependency missing
