# Requirement coverage

Implementation and validation status: **5 November 2026** (full regression results from 4 November). See [VALIDATION.md](VALIDATION.md) for measured results and raw evidence. “Implemented” describes source behavior; a passing test does not establish production readiness.

| Requirement | Implementation and evidence | Status |
| --- | --- | --- |
| Existing application and stack | Original sign-in defect fixed; React/TypeScript, Java 17 Spring Boot modular monolith, PostgreSQL and Compose retained. Production images built and three services started healthy. | Implemented and tested |
| Authentication and roles | BCrypt passwords from environment, HttpOnly/SameSite sessions, CSRF, logout; founder-only rate writes/mapping rollback enforced by backend. Integration and browser checks. | Implemented and tested |
| Five report types, CSV/XLSX | Streaming CSV / SAX XLSX; bounded files/rows/cells; dates, amounts, required fields; persisted progress, history and row errors. All five XLSX scenarios exercised through HTTP. | Implemented and tested |
| Duplicate prevention and source evidence | File hash uniqueness, partner-scoped order/transaction identities; split payments use distinct transaction IDs. Original file, raw rows, mapping IDs and row references retained. | Implemented and tested |
| Mapping review and versioning | Manual/saved mappings, 20-row preview, actor/exact-mapping approval binding, immutable versions and founder rollback. | Implemented and tested |
| Genuine AI mapping | Backend-only OpenAI Responses integration, strict schema and semantic validation, bounded body/timeout, explicit unavailable/manual fallback. Real HTTP transport exercised against a local fixture server. | Implemented; live provider call unverified |
| AI missing-ID matches | Redacted same-partner candidate facts, validated/persisted proposals, actor/order/transaction binding, expiry and explicit approval. No automatic ledger link. | Implemented; fixture approval flow tested; live provider call unverified |
| Deterministic financial rules | NUMERIC/BigDecimal, HALF_UP, strict greater-than-₹10 flag, split receipts, separate TCS/TDS, signed refunds/adjustments, pending/manual review and future-date exclusion. | Implemented and tested |
| Rate cards | Founder-editable channel/courier versions, inclusive effective dates, overlap rejection and period replacement; calculation/rate IDs in evidence. Flat rates and calendar-day schedules documented in [DECISIONS.md](DECISIONS.md). | Implemented and tested |
| Return reconciliation | Expected returns versus warehouse receipts; configurable 14-day default or explicit deadline; overdue missing inventory exception. | Implemented and tested |
| Claims and evidence | Exception-based creation, concurrent duplicate/family prevention, partner/search/status filters, notes, audit, immutable creation snapshot and JSON export. External submission is deliberately absent. | Implemented and tested |
| Dashboard and core screens | Seven core screens plus rate management; real database totals by partner/type/age and separate taxes. Server pagination/search/filtering for imports, orders, claims and unmatched movements. | Implemented and tested |
| UI quality | INR formatting, neutral/green design, accessible labels/focus/status states, loading/error/empty states; finance and mobile founder browser journeys. | Implemented; smoke-tested, not a full accessibility audit |
| Original examples and synthetic data | All three examples retained; synthetic generator/loader covers success, split/duplicate/invalid rows, COD, freight, returns and taxes without real personal data. | Implemented and tested |
| Performance | Streaming, 1,000-row batches, set-based updates, incremental reconciliation and indexes; reproducible complete HTTP benchmark. Both target-size and larger stress results are reported without substituting domain-only timings. | See measured results in [VALIDATION.md](VALIDATION.md) |
| Migrations and validation | Flyway V1–V4; 31 backend tests including 11 real PostgreSQL integration tests; frontend build; three production browser workflows; Docker build/startup checks. | Passed; raw summaries linked in validation report |
| Documentation | [README](../README.md), [deployment](../deployment.md), [technical design source](TECHNICAL_DESIGN.md), assumptions, validation and this coverage matrix. No invented deployment/GitHub URL. | Complete |

## External dependency

On 5 November, the backend loaded the user's private API key and `gpt-6-luna` model configuration. The live provider rejected requests with HTTP 429, `credit_balance_exhausted` / `insufficient_quota`. The same synthetic report then completed through manual mapping and deterministic reconciliation. Successful live mapping and match quality remain unverified until API credits are available; no success is claimed for the rejected request. Run `scripts/verify-live-ai.py` after restoring credits to exercise both proposal flows and explicit approval. See the [recorded attempt](validation/live-ai-attempt-2026-11-05.json).

## Deliberate limits

One workspace and one backend instance are supported. Sessions/jobs are local to that instance. Existing order identities are rejected rather than revised. Weight/zone courier contracts, bank-holiday calendars, partial-item returns, large-XLSX throughput, concurrent large-import resilience and a full accessibility/security audit need separate product inputs or validation. These limits and the retained measurements are described in the design and validation documents.

No push, publication or external claim submission was performed.
