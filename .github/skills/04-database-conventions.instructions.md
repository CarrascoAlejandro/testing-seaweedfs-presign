---
description: This skill documents the SQL Server database conventions used in Cirrus/ProMujer microservices.
---
# Database Conventions

This skill documents the SQL Server database conventions used in Cirrus/ProMujer microservices.

> **⚠️ IMPORTANT: Database scripts are provided by the Database Team.**
> 
> Developers should **NOT** write SQL migration files. If you identify a need for schema changes:
> 1. Document the requirements
> 2. Submit a request to the Database Team
> 3. Validate received scripts against these conventions
>
> If received scripts violate conventions below, **alert the Database Team** before integrating.

## When to Use

Reference this skill when:
- Receiving new migration scripts from the Database Team
- Validating schema changes for convention compliance
- Creating JPA entities to map existing tables
- Writing repository queries (JPQL or native SQL)
- Debugging database-related issues

---

## File Conventions

### Migration File Location

```
src/main/resources/db/
├── V1__create_tf_transfer.sql
├── V2__add_beneficiary_tables.sql
└── V3__update_transfer_indexes.sql
```

### File Naming Convention

```
V{N}__{description}.sql
```

| Component | Rule | Example |
|-----------|------|---------|
| `V` | Literal prefix | `V` |
| `{N}` | Sequential version number | `1`, `2`, `3` |
| `__` | Double underscore separator | `__` |
| `{description}` | Snake_case description | `create_tf_transfer` |
| `.sql` | File extension | `.sql` |

**Valid examples:**
- `V1__create_tf_transfer.sql`
- `V2__add_beneficiary_tables.sql`
- `V10__update_catalog_options.sql`

**Invalid examples:**
- `v1_create_transfer.sql` (lowercase, single underscore)
- `V1-create-transfer.sql` (wrong separator)
- `create_transfer.sql` (missing version prefix)

---

## Table Naming Conventions

### Prefix System

| Prefix | Domain | Examples |
|--------|--------|----------|
| `tf_` | Transfer | `tf_transfer` |
| `be_` | Beneficiary | `be_beneficiary` |
| `pb_` | PocketBank (core/shared) | `pb_catalog`, `pb_user`, `pb_device` |
| `h_` | History (shadow tables) | `h_tf_transfer` |
| `ob_` | Onboarding | `ob_user`, `ob_request` |
| `ct_` | Catalog (lookup tables) | `ct_transfer_status`, `ct_transfer_type` |

### Naming Rules

1. **Singular nouns:** `tf_transfer` not `tf_transfers`
2. **Snake_case:** `tf_transfer` not `tfTransfer`
3. **Descriptive:** `be_beneficiary` not `be_ben`

### History Tables

Every transactional table should have a corresponding history table:

| Main Table | History Table |
|------------|---------------|
| `tf_transfer` | `h_tf_transfer` |
| `be_beneficiary` | `h_be_beneficiary` |

---

## Column Conventions

### Required Columns

Every table must include:

| Column | Type | Purpose |
|--------|------|---------|
| `{table}_id` | `INT` | Primary key |
| `version` | `INT` | Optimistic locking |
| `created_at` | `DATETIME` | Record creation timestamp |
| `updated_at` | `DATETIME` | Last modification timestamp |

### Naming Rules

1. **Primary key:** `{entity}_id` (e.g., `transfer_id`, `beneficiary_id`)
2. **Foreign keys:** `{referenced_entity}_id` (e.g., `customer_id`, `catalog_id`)
3. **Snake_case:** `origin_account_number` not `originAccountNumber`
4. **Boolean columns:** `is_` prefix (e.g., `is_active`, `is_verified`)
5. **Status columns:** `status` (string) or `status_id` (FK to catalog)

### Example Structure

```sql
-- EXPECTED: Properly structured table
CREATE TABLE tf_transfer (
    transfer_id         INT NOT NULL,                    -- PK
    customer_id         VARCHAR(50) NOT NULL,            -- FK reference
    origin_account_number VARCHAR(30) NOT NULL,
    target_account_number VARCHAR(30) NOT NULL,
    amount              DECIMAL(18,2) NOT NULL,
    currency_id         INT NOT NULL,
    status              VARCHAR(30) NOT NULL,
    watchdog_business_key VARCHAR(50) NULL,
    description         VARCHAR(255) NULL,
    version             INT NOT NULL DEFAULT 0,          -- Required
    created_at          DATETIME NOT NULL DEFAULT GETDATE(),  -- Required
    updated_at          DATETIME NULL,                   -- Required
    CONSTRAINT pk_tf_transfer PRIMARY KEY (transfer_id)
);
```

---

## Sequence Conventions

### Naming

```
sq_{table}_id
```

**Examples:**
- `sq_tf_transfer_id`
- `sq_be_beneficiary_id`

### Definition

```sql
CREATE SEQUENCE sq_tf_transfer_id
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    NO CYCLE
    CACHE 10;
```

### Usage in Repository

```java
@Repository
public interface TfTransferRepository extends JpaRepository<TfTransfer, Integer> {

    @Query(value = "SELECT NEXT VALUE FOR sq_tf_transfer_id", nativeQuery = true)
    Integer getNextSequenceValue();
}
```

---

## History Pattern (Audit Trail)

### Purpose
Automatically record all changes to transactional data for audit and recovery.

### Structure

#### History Table

```sql
-- History table mirrors main table with additional audit columns
CREATE TABLE h_tf_transfer (
    h_transfer_id       INT IDENTITY(1,1) NOT NULL,      -- Auto-increment
    transfer_id         INT NOT NULL,                     -- Original PK
    customer_id         VARCHAR(50) NOT NULL,
    origin_account_number VARCHAR(30) NOT NULL,
    target_account_number VARCHAR(30) NOT NULL,
    amount              DECIMAL(18,2) NOT NULL,
    currency_id         INT NOT NULL,
    status              VARCHAR(30) NOT NULL,
    watchdog_business_key VARCHAR(50) NULL,
    description         VARCHAR(255) NULL,
    version             INT NOT NULL,
    created_at          DATETIME NOT NULL,
    updated_at          DATETIME NULL,
    h_created_at        DATETIME NOT NULL DEFAULT GETDATE(),  -- When history record was created
    h_operation         VARCHAR(10) NOT NULL,             -- 'INSERT' or 'UPDATE'
    CONSTRAINT pk_h_tf_transfer PRIMARY KEY (h_transfer_id)
);
```

#### Triggers

```sql
-- INSERT trigger
CREATE TRIGGER h_tg_insert_historic_tf_transfer
ON tf_transfer
AFTER INSERT
AS
BEGIN
    INSERT INTO h_tf_transfer (
        transfer_id, customer_id, origin_account_number, target_account_number,
        amount, currency_id, status, watchdog_business_key, description,
        version, created_at, updated_at, h_operation
    )
    SELECT 
        transfer_id, customer_id, origin_account_number, target_account_number,
        amount, currency_id, status, watchdog_business_key, description,
        version, created_at, updated_at, 'INSERT'
    FROM inserted;
END;
GO

-- UPDATE trigger
CREATE TRIGGER h_tg_update_historic_tf_transfer
ON tf_transfer
AFTER UPDATE
AS
BEGIN
    INSERT INTO h_tf_transfer (
        transfer_id, customer_id, origin_account_number, target_account_number,
        amount, currency_id, status, watchdog_business_key, description,
        version, created_at, updated_at, h_operation
    )
    SELECT 
        transfer_id, customer_id, origin_account_number, target_account_number,
        amount, currency_id, status, watchdog_business_key, description,
        version, created_at, updated_at, 'UPDATE'
    FROM inserted;
END;
GO
```

---

## Catalog Pattern (Lookup Tables)

### Purpose
Store static reference data (statuses, types, categories) in a flexible master-detail structure.

### Structure

```sql
-- Master catalog
CREATE TABLE pb_catalog (
    catalog_id          INT NOT NULL,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(255) NULL,
    status              INT NOT NULL DEFAULT 1,
    CONSTRAINT pk_pb_catalog PRIMARY KEY (catalog_id)
);

-- Detail options
CREATE TABLE pb_catalog_option (
    catalog_option_id   INT NOT NULL,
    catalog_id          INT NOT NULL,
    code                VARCHAR(50) NOT NULL,
    value               VARCHAR(255) NOT NULL,
    sort_order          INT NOT NULL DEFAULT 0,
    status              INT NOT NULL DEFAULT 1,
    CONSTRAINT pk_pb_catalog_option PRIMARY KEY (catalog_option_id),
    CONSTRAINT fk_catalog_option_catalog FOREIGN KEY (catalog_id) 
        REFERENCES pb_catalog(catalog_id)
);
```

### Sample Data

```sql
-- Catalog: Transfer Status
INSERT INTO pb_catalog (catalog_id, name, description) 
VALUES (1, 'TRANSFER_STATUS', 'Transfer status codes');

INSERT INTO pb_catalog_option (catalog_option_id, catalog_id, code, value, sort_order) VALUES
(101, 1, '2FA_REQUESTED', 'Awaiting 2FA verification', 1),
(102, 1, '2FA_CONFIRMED', '2FA verified', 2),
(103, 1, 'SUCCESSFUL', 'Transfer completed', 3),
(104, 1, 'ERROR', 'Transfer failed', 4),
(105, 1, 'ERROR_INVOKE', 'External service error', 5);

-- Catalog: Transfer Type
INSERT INTO pb_catalog (catalog_id, name, description) 
VALUES (2, 'TRANSFER_TYPE', 'Transfer type codes');

INSERT INTO pb_catalog_option (catalog_option_id, catalog_id, code, value, sort_order) VALUES
(201, 2, 'OWN', 'Own accounts transfer', 1),
(202, 2, 'THIRD', 'Third party transfer', 2),
(203, 2, 'OTHER', 'Interbank transfer (ACH)', 3);
```

---

## Index Conventions

### Naming

```
ix_{table}_{column(s)}
```

**Examples:**
- `ix_tf_transfer_customer_id`
- `ix_tf_transfer_watchdog_business_key`
- `ix_be_beneficiary_customer_account` (composite)

### Required Indexes

1. **Primary key:** Automatic
2. **Foreign keys:** Should be indexed
3. **Query columns:** Columns frequently used in WHERE clauses
4. **Business keys:** Unique identifiers for external systems

```sql
CREATE INDEX ix_tf_transfer_customer_id 
    ON tf_transfer(customer_id);

CREATE UNIQUE INDEX ix_tf_transfer_watchdog_business_key 
    ON tf_transfer(watchdog_business_key) 
    WHERE watchdog_business_key IS NOT NULL;

CREATE INDEX ix_tf_transfer_status_created 
    ON tf_transfer(status, created_at DESC);
```

---

## JPA Entity Mapping

### Mapping Tables to Entities

```java
@Entity
@Table(name = "tf_transfer")  // Matches table name exactly
public class TfTransfer {

    @Id
    @Column(name = "transfer_id")  // Matches column name exactly
    private Integer transferId;

    @Column(name = "customer_id", length = 50, nullable = false)
    private String customerId;

    @Column(name = "origin_account_number", length = 30, nullable = false)
    private String originAccountNumber;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency_id", nullable = false)
    private Integer currencyId;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "watchdog_business_key", length = 50)
    private String watchdogBusinessKey;

    @Version  // Optimistic locking
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters and setters...
}
```

### Mapping Relationships

```java
@Entity
@Table(name = "pb_catalog")
public class PbCatalog {

    @Id
    @Column(name = "catalog_id")
    private Integer catalogId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @OneToMany(mappedBy = "catalog", fetch = FetchType.LAZY)
    private List<PbCatalogOption> options;
}

@Entity
@Table(name = "pb_catalog_option")
public class PbCatalogOption {

    @Id
    @Column(name = "catalog_option_id")
    private Integer catalogOptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private PbCatalog catalog;

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Column(name = "value", length = 255, nullable = false)
    private String value;
}
```

---

## Validation Checklist

Use this checklist when reviewing migration scripts from the Database Team:

### File Validation

- [ ] File follows naming convention: `V{N}__{description}.sql`
- [ ] Version number is sequential (no gaps or duplicates)
- [ ] Description is meaningful and snake_case

### Table Validation

- [ ] Table name uses correct prefix (`tf_`, `be_`, `pb_`, etc.)
- [ ] Table name is singular and snake_case
- [ ] Primary key follows `{entity}_id` convention
- [ ] Required columns present: `version`, `created_at`, `updated_at`
- [ ] History table created if table is transactional

### Column Validation

- [ ] Column names are snake_case
- [ ] Foreign keys use `{entity}_id` naming
- [ ] Boolean columns use `is_` prefix
- [ ] Appropriate lengths for VARCHAR columns
- [ ] DECIMAL precision/scale appropriate for money (18,2)

### Sequence Validation

- [ ] Sequence follows `sq_{table}_id` naming
- [ ] Sequence starts at 1, increments by 1
- [ ] Corresponding repository method exists for next value

### Index Validation

- [ ] Index follows `ix_{table}_{column}` naming
- [ ] Foreign key columns are indexed
- [ ] Frequently queried columns are indexed
- [ ] Unique constraints where appropriate

### History Pattern Validation

- [ ] History table follows `h_{table}` naming
- [ ] History table includes `h_created_at` and `h_operation` columns
- [ ] INSERT trigger created: `h_tg_insert_historic_{table}`
- [ ] UPDATE trigger created: `h_tg_update_historic_{table}`

---

## Common Issues

### Alert Database Team If:

1. **Missing version column** — Required for JPA optimistic locking
2. **Missing timestamp columns** — `created_at` and `updated_at` required
3. **Incorrect naming** — Tables, columns, or indexes not following conventions
4. **Missing history table** — Transactional tables need audit trail
5. **Missing triggers** — History tables need INSERT/UPDATE triggers
6. **Missing indexes** — Foreign keys and frequently queried columns
7. **Sequence not created** — Tables using sequence-based IDs

### Example Alert Message

```
Database Migration Review - V3__add_payment_table.sql

Issues found:
1. Table `payment` should be named `tf_payment` (missing prefix)
2. Primary key `id` should be named `payment_id`
3. Missing `version` column for optimistic locking
4. Missing `created_at` and `updated_at` columns
5. No history table `h_tf_payment` defined
6. No sequence `sq_tf_payment_id` defined

Please update the migration script to follow project conventions.
```
