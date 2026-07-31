# Strongly Typed Built-in Facts Design

## Goal

Replace built-in facts that use strings for closed or numeric domains with compile-time value types. Host code must no longer be able to submit arbitrary strings for sensitivity levels, boolean flags, or baseline ratios. Event attributes and persistence remain stable strings so this change requires no schema migration and does not alter rule storage contracts.

## Scope

The following facts become strongly typed:

| Fact token | Current type | New type |
| --- | --- | --- |
| `Sensitivity` | `String` | `BuiltInFacts.SensitivityLevel` |
| `DifferentNetworks` | `String` | `Boolean` |
| `SequentialAccess` | `String` | `Boolean` |
| `Sensitive` | `String` | `Boolean` |
| `WorkHours` | `String` | `Boolean` |
| `PrivilegeIncrease` | `String` | `Boolean` |
| `HighPrivilege` | `String` | `Boolean` |
| `BaselineRatio` | `String` | `BigDecimal` |

`ResourceId` and `TargetUserId` remain open strings. `DataCount` remains `Long`.

This is an intentional public API change. Typed fact producers must pass the new value types; no string-input compatibility overload is provided.

## Type Model

`BuiltInFacts` exposes `SensitivityLevel` with exactly four values:

- `LOW`
- `MEDIUM`
- `HIGH`
- `INTERNAL`

The existing `Sensitivity` token remains the fact identity but implements `BuiltInFactType<SensitivityLevel>`. Boolean token classes implement `BuiltInFactType<Boolean>`. `BaselineRatio` implements `BuiltInFactType<BigDecimal>`.

`BigDecimal` is used instead of `Double` because it rejects NaN and infinity by construction and provides deterministic decimal persistence. Baseline ratios must be non-negative.

## Canonical Codecs

`FactDefinition` adds reusable codec factories for the closed and numeric domains:

- Enum codec: encodes enum names as lowercase ASCII and decodes case-insensitively through the declared enum type.
- Boolean codec: encodes only `true` or `false` and rejects any other persisted representation during decode.
- BigDecimal codec: normalizes values with `stripTrailingZeros`, canonicalizes zero, encodes with `toPlainString`, and decodes with the `BigDecimal` constructor.

All codecs continue through the existing normalization, maximum-length, and validator path. A raw value of the wrong Java type fails with `IllegalArgumentException` before encoding.

## Event Boundary

`ActionFacts` remains the typed in-memory carrier. `SecurityEventAssembler` reads enum, Boolean, and BigDecimal values and uses the corresponding `BuiltInFacts` definition to encode them before adding event attributes.

The resulting event representation remains unchanged:

- `SensitivityLevel.HIGH` becomes `sensitivity=high`.
- `Boolean.TRUE` becomes `sequential_access=true`.
- `new BigDecimal("3.50")` becomes `baseline_ratio=3.5`.

Existing database columns, extension fact text, management queries, and rule predicates therefore retain their string transport contract. No SQL schema change is required.

## Call-site Migration

All repository-owned producers and tests migrate atomically:

- `ActionFacts.Builder.put` calls use enum, Boolean, or BigDecimal values.
- `MonitoringFacts.put` calls use the same strong types.
- `@ActionFact` method parameters use the token's new declared Java type.
- Spring 2 and Spring 3 integration audit modules retain equivalent behavior and persisted attribute assertions.

The startup contract validator continues to compare method parameter types with `FactDefinition.getValueType`, so stale string-based `@ActionFact` methods fail during application initialization rather than at first request.

## Compatibility And Errors

There is no compatibility layer for typed string inputs. Code that submits `"high"`, `"true"`, or `"3.5"` must be updated and will otherwise fail at compilation when using the generic typed APIs, or at runtime when entering through an untyped integration boundary.

Previously persisted canonical strings remain readable through the new codecs. Enum decoding accepts case differences to tolerate existing uppercase sensitivity values. Boolean decoding accepts only case-insensitive `true` and `false`. Invalid numeric, negative ratio, unknown enum, and invalid boolean values fail validation without exposing the rejected value in the exception message.

## Testing

Tests follow a red-green migration:

1. API codec tests define the required enum, Boolean, and BigDecimal behavior and verify string raw values are rejected.
2. Event assembler tests verify typed inputs produce the existing canonical string attributes.
3. Spring support and Starter tests verify runtime facts and `@ActionFact` parameter contracts use the new types.
4. Spring 2 and Spring 3 integration audit tests verify privilege, query, sensitive export, and rule behavior remain unchanged.
5. The complete Maven reactor runs with tests enabled.

## Non-goals

- Changing `SecurityEvent` attribute storage from strings to typed columns.
- Migrating database schemas.
- Introducing host-defined enum registries.
- Changing rule thresholds or detection semantics.
- Converting open identifiers or arbitrary host facts into enums.
