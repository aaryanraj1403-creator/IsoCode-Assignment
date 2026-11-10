# Decisions and explicit assumptions

## Financial meaning

- Every financial identifier is scoped by the selected partner. Partner names are case-sensitive; the current implementation uses names rather than a separate partner directory.
- Money is validated as a plain decimal with at most two fractional digits and stored as PostgreSQL NUMERIC. Java domain calculations use BigDecimal and HALF_UP rounding.
- Commission is rounded once to paise before fixed fees and taxes are deducted. SQL NUMERIC rounding for the nonnegative commission base follows the same rule.
- Difference means received minus expected. A discrepancy is actionable only when its absolute amount is strictly greater than ₹10. Exactly ₹10 is within tolerance.
- A due date is inclusive. Shortfalls are pending on that date and become overdue the following day in Asia/Kolkata.
- Signed settlement/COD amounts represent net receipts under the channel rule; refunds and negative adjustments retain their signs. Taxes are transaction-level deductions, not a repeated order-level total on every split.
- Future-dated payments, taxes, freight and warehouse receipts remain in evidence but are excluded from reconciliation until their recorded event date in Asia/Kolkata. A scheduled future receipt is not cash or inventory already received.
- TCS and TDS are separately reported and deducted from expected settlement; they are not leakage. This also applies to imported returned orders.
- Freight reports represent separately billed courier charges. Do not also net those same charges from the settlement amount; a provider that nets freight needs explicit normalization before importing. The current model does not infer that distinction.
- Concurrent creation is serialized with reconciliation. Only one active settlement claim may cover an order across UNPAID_PAST_DUE and SHORT_PAID; independent freight/return claims remain valid.
- A claim status records a workflow decision. Resolving a claim does not fabricate a receipt or clear an unreconciled gap.

## Seed records and assumed rate cards

The three original examples are retained exactly, including their expected/received values. Their assumed due date remains 25 October 2026. They use a labelled brief override because the source brief did not supply complete commercial terms. Imported orders cannot supply that override.

| Partner | Rule | Commission | Fixed fee | Return fee | Settlement delay |
| --- | --- | --- | --- | --- | --- |
| Website | CHANNEL | 0% | ₹90.00 | ₹96.00 | 7 calendar days |
| Marketplace 1 | CHANNEL | 20% | ₹60.20 | ₹96.00 | 14 calendar days |
| Website | COURIER | 0% | ₹70.00 | ₹96.00 | 7 calendar days |
| Marketplace 1 | COURIER | 0% | ₹70.00 | ₹96.00 | 7 calendar days |

These are demonstration assumptions, not asserted partner contracts. Seed periods are 1 January 2020 through 31 December 2099. Rate-card periods are inclusive and cannot overlap for one partner/kind through the application API.

For a delivered imported order: expected = value − rounded commission − fixed channel fee − TCS − TDS. For a returned/return-expected order: expected = −return fee − TCS − TDS. Courier charges use a flat forward/return fee; weight slabs, zones, GST fee components and partial-item returns are future rule extensions.

The order date selects the rate version. An explicit due_date wins; otherwise the channel’s settlement delay is added to the order date. This is calendar-day arithmetic, not a bank-holiday calendar. Replacing a period closes the predecessor one day before the new version starts. Claim evidence keeps the calculation and rate IDs captured at claim creation.

## Returns and money at risk

- ORDERS.delivery=RETURN_EXPECTED means inventory is expected back; RETURNS reports are warehouse receipts.
- RETURNED / Returned to warehouse in an order is also treated as warehouse received.
- The default return deadline is order date + RETURN_GRACE_DAYS (14), overridden by an explicit return_due. No delivery/return-initiation timestamp was supplied, so order date is the documented fallback.
- An overdue missing return is valued at the order’s value, not margin or cost of goods. Values at or below ₹10 do not create a financial flag.
- A valid matched receipt clears the missing-return exception on the next reconciliation.
- Order risk is the sum of its overdue settlement gap, separately billed freight overcharge and missing inventory value. These components are shown separately in evidence. Unknown rate cards produce manual review instead of an invented expected amount.
- Unmatched transactions remain visible for human review and are excluded from order totals until approved against a same-partner order.

## Imports, mappings and evidence

Files are hashed by bytes and scoped by partner/report type. Renaming the same file does not import it twice. Transactions are unique across settlement/COD/freight/return report types for one partner; split settlements require distinct transaction IDs.

Duplicate existing orders are rejected, rather than silently revising their delivery/value fields. A future order-revision workflow must preserve prior versions and explicit authorization. A malformed file fails as a batch; structurally valid files can complete with valid rows accepted and invalid/duplicate rows retained as errors.

Mappings are canonical field → source header. Saved mappings are filtered to headers actually present in the new upload. Exact header matches are labelled manual defaults. Approval requires a validated preview of that exact mapping by the approving account. Mapping rollback inserts a new version and does not reinterpret historical imports.

Original bytes, SHA-256, source row number, raw values and mapping version remain available. Claims copy the order calculation, rate IDs and source references/values at creation. Original files are downloadable separately; evidence export is JSON.

## AI and deployment tradeoffs

Mapping suggestions send only column headers to OpenAI, with storage disabled. Record-match suggestions send opaque candidate tokens and redacted facts: known payment/delivery/status labels, relative day gaps and whether an amount fits a server-computed rule. No raw source rows, actual order/transaction identifiers, financial values or keys are sent as examples. Headers and provider responses are untrusted. Schema and semantic checks reject unknown/duplicate fields or headers, invalid confidence and incomplete suggestions. Confidence below 0.85 remains manual. AI never computes money, approves mappings, links orders automatically or changes claims.

Missing-ID matching supports manual selection and genuine provider-backed proposals. At most 20 eligible same-partner orders are compared deterministically; the provider can rank up to five opaque candidates. No match is applied by the suggestion endpoint. A stored proposal must be explicitly approved by its creating account for the same transaction/order within 24 hours. Stale/superseded proposals are rejected, and approval is audited. Without a provider key, the application offers manual matching and does not fabricate AI proposals.

The backend is one deployable Spring application with domain/import/reconciliation/claim modules represented by separate services/controllers. Batched JDBC and set-based SQL keep processing bounded and observable. This trades a distributed job system for simpler local deployment. One backend instance is supported; durable multi-instance jobs, session sharing, login abuse controls, a partner directory and source retention policies are production next steps.

See [VALIDATION.md](VALIDATION.md) for executed checks and [TECHNICAL_DESIGN.md](TECHNICAL_DESIGN.md) for the complete design source.
