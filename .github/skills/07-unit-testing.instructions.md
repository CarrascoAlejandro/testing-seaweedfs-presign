---
applyTo: '**/test/**/*BlTest.java'
description: This skill documents how to write unit tests for the BL (Business Logic) layer in Cirrus/ProMujer microservices using JUnit 5 and Mockito.
---
# Unit Testing — BL Layer

This skill documents how to write unit tests for Business Logic classes in this service. Tests are pure unit tests (no Spring context) using JUnit 5 + Mockito.

## When to Use

Reference this skill when:
- Writing unit tests for a new `{Entity}Bl` class
- Adding test coverage to an existing BL class
- Reviewing tests for correctness and completeness

---

## Key Rules

1. **Mock everything except Validation** — repositories, mappers, `RequestStepUpdater`, and any other `@Component`/`@Service` dependency are mocked. Validation classes (`{Entity}Validation`) are instantiated directly as real objects so the actual validation logic is exercised.
2. **No Spring context** — use `@ExtendWith(MockitoExtension.class)`. Tests run in milliseconds.
3. **Instantiate the BL manually in `@BeforeEach`** — inject the real validation instance alongside the mocks.
4. **One assertion per concern** — each test verifies exactly one failure path or one success path.

---

## Test Class Structure

```java
@ExtendWith(MockitoExtension.class)
class EmploymentSituationBlTest {

    // --- mocks (all dependencies EXCEPT validation) ---
    @Mock private ObUserRepository userRepository;
    @Mock private ObEmploymentSituationRepository employmentSituationRepository;
    @Mock private EmploymentSituationMapper employmentSituationMapper;
    @Mock private RequestStepUpdater requestStepUpdater;

    // --- system under test ---
    private EmploymentSituationBl bl;

    @BeforeEach
    void setUp() {
        // Real validation instance — NOT a mock
        EmploymentSituationValidation validation = new EmploymentSituationValidation();
        bl = new EmploymentSituationBl(userRepository, employmentSituationRepository,
                employmentSituationMapper, validation, requestStepUpdater);
    }
}
```

---

## Helper Methods

Define private helpers to avoid repetition. Three categories are always useful:

### 1. Entity builder — a valid, fully populated entity

```java
private ObUser activeUser() {
    ObRequest request = new ObRequest();
    request.setId(100L);
    ObUser user = new ObUser();
    user.setId(1L);
    user.setBirthDate(LocalDate.of(1990, 1, 1));
    user.setRequest(request);
    return user;
}
```

For BL classes that check marital status or status fields, set those too:

```java
private ObUser marriedActiveUser() {
    ObUser user = activeUser();
    user.setStatus((short) 1);
    user.setCatMaritalStatus("2"); // married
    return user;
}
```

### 2. DTO builder — a valid request DTO

```java
private EmploymentSituationRequestDto validCode4Request() {
    EmploymentSituationRequestDto dto = new EmploymentSituationRequestDto();
    dto.setEmploymentSituationCode("4");
    dto.setMonthlyIncomeRangeCode("RANGE_1");
    return dto;
}
```

### 3. Audit builder

```java
private OnboardingAuditDto audit() {
    return new OnboardingAuditDto("user1", "device1", "-17.0", "-65.0", "APP", "127.0.0.1");
}
```

### 4. Stub helper — full success path mock setup

Extract the repetitive happy-path stubbing into a single method:

```java
private void stubSuccessfulSave() {
    ObEmploymentSituation saved = new ObEmploymentSituation();
    saved.setId(400L);
    when(employmentSituationRepository.findByRequest_IdAndStatus(anyLong(), anyShort()))
            .thenReturn(Optional.empty());
    when(employmentSituationMapper.toEntity(any(), any(), any(), any(), any())).thenReturn(saved);
    when(employmentSituationRepository.save(any())).thenReturn(saved);
}
```

---

## Asserting Thrown Exceptions

Use JUnit 5's `assertThrows` and then assert the code with AssertJ:

```java
@Test
void registerEmploymentSituation_userNotFound_throwsServiceException() {
    when(userRepository.findByIdAndStatus(anyLong(), anyShort())).thenReturn(Optional.empty());

    ServiceException ex = assertThrows(ServiceException.class,
            () -> bl.registerEmploymentSituation(1L, validCode4Request(), audit()));

    assertThat(ex.getCode()).isEqualTo(EmploymentSituationValidation.CODE_USER_NOT_FOUND);
}
```

Always assert against the **public constant** on the Validation class, not a raw integer literal — except when the code is private to the BL (e.g., an inline parse error code):

```java
// BL has: private static final int CODE_INVALID_DATE = 4007;
// since it's private, use the literal
assertThat(ex.getCode()).isEqualTo(4007);
```

---

## Test Groups — Standard Coverage Checklist

For every BL method, write tests in this order:

### 1. User / request guard failures

```java
@Test void method_userNotFound_throwsServiceException()
@Test void method_userInactive_throwsServiceException()      // if validation checks status
@Test void method_requestIsNull_throwsServiceException()
@Test void method_requestIdIsNull_throwsServiceException()
```

For "user not found", return `Optional.empty()` from the repo:

```java
when(userRepository.findByIdAndStatus(anyLong(), anyShort())).thenReturn(Optional.empty());
```

For "user inactive", return a user with `status = 0`:

```java
ObUser user = new ObUser();
user.setStatus((short) 0);
when(userRepository.findByIdAndStatus(anyLong(), anyShort())).thenReturn(Optional.of(user));
```

For "null request":

```java
ObUser user = new ObUser();
user.setStatus((short) 1);
user.setRequest(null);
when(userRepository.findByIdAndStatus(anyLong(), anyShort())).thenReturn(Optional.of(user));
```

### 2. Business rule failures (one test per distinct rule)

```java
@Test void method_duplicateEntry_throwsServiceException()
@Test void method_limitReached_throwsServiceException()
@Test void method_invalidFieldFormat_throwsServiceException()
@Test void method_conflictingValues_throwsServiceException()
```

Only stub what is needed to reach the failing validation. If validation A throws before the code reaches repository call B, do not stub B.

### 3. Side effects — verify inactivation of existing records

```java
@Test
void registerSpouse_existingActiveSpouseIsInactivated() {
    when(userRepository.findByIdAndStatus(anyLong(), anyShort()))
            .thenReturn(Optional.of(marriedActiveUser()));

    ObSpouse existingSpouse = new ObSpouse();
    existingSpouse.setId(50L);
    existingSpouse.setStatus((short) 1);
    when(spouseRepository.findByRequest_IdAndStatus(anyLong(), anyShort()))
            .thenReturn(Optional.of(existingSpouse));

    ObSpouse newSpouse = new ObSpouse();
    newSpouse.setId(51L);
    when(spouseMapper.toEntity(any(), any(), any(), any())).thenReturn(newSpouse);
    when(spouseRepository.save(any())).thenReturn(newSpouse);

    bl.registerSpouse(1L, validRequest(), audit());

    // Verify the existing entity was mutated in-place
    assertThat(existingSpouse.getStatus()).isEqualTo((short) OnboardingConstants.STATUS_INACTIVE);
    // Verify save was called for both inactivation and the new record
    verify(spouseRepository, times(2)).save(any());
}
```

### 4. Mutation / normalization — verify in-place DTO mutations

When the BL mutates the request DTO (e.g., normalizing names), hold a reference to the DTO before the call and assert its state afterward:

```java
@Test
void registerSpouse_allNamesAreNormalized() {
    when(userRepository.findByIdAndStatus(anyLong(), anyShort()))
            .thenReturn(Optional.of(marriedActiveUser()));
    stubSuccessfulSave();

    SpouseRequestDto dto = new SpouseRequestDto();
    dto.setFirstName("maria  jose");
    dto.setFirstSurname("garcia  lopez");
    dto.setDocumentTypeCode("CI");
    dto.setDocumentNumber("1234567");

    bl.registerSpouse(1L, dto, audit());

    assertThat(dto.getFirstName()).isEqualTo("MARIA JOSE");
    assertThat(dto.getFirstSurname()).isEqualTo("GARCIA LOPEZ");
}
```

### 5. Conditional logic — verify step updates

```java
@Test
void method_conditionNotMet_doesNotUpdateStep() {
    // ... stub for success with condition = false ...
    stubSuccessfulSave();

    bl.method(1L, validRequest(), audit());

    verify(requestStepUpdater, never()).updateLastStepCompleted(any(), anyString(), anyInt());
}

@Test
void method_conditionMet_updatesStep() {
    // ... stub for success with condition = true ...
    stubSuccessfulSave();

    bl.method(1L, validRequest(), audit());

    verify(requestStepUpdater).updateLastStepCompleted(
            any(ObRequest.class), anyString(), eq(OnboardingConstants.SOME_STEP));
}
```

### 6. Success — verify the returned DTO

```java
@Test
void method_success_returnsResponseWithId() {
    when(userRepository.findByIdAndStatus(anyLong(), anyShort()))
            .thenReturn(Optional.of(activeUser()));
    stubSuccessfulSave();

    SomeResponseDto result = bl.method(1L, validRequest(), audit());

    assertThat(result).isNotNull();
    assertThat(result.getSomeId()).isEqualTo(400L);     // the ID from the saved entity mock
    assertThat(result.getMessage()).isNotBlank();
}
```

---

## Mockito Argument Matchers

Use generic matchers when the exact value doesn't matter for the test's intent:

```java
// Repository finders
when(userRepository.findByIdAndStatus(anyLong(), anyShort())).thenReturn(...);

// Boolean repository methods
when(repo.existsByRequest_IdAndField(anyLong(), anyString(), anyString())).thenReturn(true);

// Count methods
when(repo.countByRequest_IdAndStatus(anyLong(), anyShort())).thenReturn(5L);

// Mapper calls (all parameters unknown at stub time)
when(mapper.toEntity(any(), any(), any(), any(), any())).thenReturn(entity);

// Verify calls
verify(repo, times(2)).save(any());
verify(repo, never()).save(any());
verify(stepUpdater).updateLastStepCompleted(any(ObRequest.class), anyString(), eq(6));
```

---

## Imports Cheat Sheet

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
```

---

## What NOT to Test

- **Validation classes themselves** — they are plain Java with no dependencies; their logic is exercised through the BL tests.
- **Mappers** — they are MapStruct-generated and compile-time verified; mock them in BL tests.
- **Repository query correctness** — that belongs in integration/slice tests (`@DataJpaTest`), not here.
- **API layer** — controllers are thin wrappers; test them with `@WebMvcTest` if needed.
