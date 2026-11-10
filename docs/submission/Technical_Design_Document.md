# Project: Reconflow — D2C Order, Payment and Return Reconciliation

Candidate: Aryan Raj

GitHub: https://github.com/aaryanraj1403-creator/IsoCode-Assignment

Live application: none. No public deployment has been provisioned or verified. The reviewer application runs locally at http://127.0.0.1:16173 (loopback only).

## 1. Problem and implemented scope

A D2C finance team receives separate order, settlement, cash-on-delivery (COD), freight and warehouse receipt reports. Reconflow retains the reports, requires approved column mappings, calculates discrepancies deterministically and builds claims with source evidence. PostgreSQL supplies the dashboard figures; the demonstration records are synthetic.

The implemented journey is sign in → upload → preview and approve mapping → reconcile → inspect exceptions → create and review claims. Both finance executive and co-founder roles can operate this flow. Co-founders additionally manage rate versions and mapping rollback. Claims are local workflow records; no external partner submission occurs.

Submission status: the GitHub API check on 10 November 2026 returned a public repository with `size: 0`, default branch `main`, still empty. This workspace contains no `.git` directory or configured remote, so remote commits, branch contents and source parity cannot be certified, and no push was performed. Publishing the reviewed project root to that repository and verifying an anonymous clone are required candidate actions.

## 2. Architecture and choices

Browser (React/TypeScript) → same-origin nginx or Vite proxy → Java 17 Spring Boot API → PostgreSQL. The API also writes original reports to persistent storage and optionally calls OpenAI from the backend.

A modular monolith keeps financial transactions, duplicate checks and claim creation within one database transaction boundary. Controllers/services separate authentication, importing, mapping, reconciliation, rate cards, claims and evidence. They remain in one Java package: these are responsibility boundaries, not independently enforced modules. Explicit domain packages are a next step.

Stack: React 18.3.1, TypeScript 5.6.3, Vite 5.4.11, Java 17, Spring Boot 3.3.5, Spring Security, JDBC, Flyway, PostgreSQL 16 in Compose, Apache Commons CSV 1.12.0, Apache POI 5.3.0 and Playwright 1.64.1. Docker uses a non-root Java runtime and nginx frontend. There is no broker or external claim integration.

Tradeoff: a bounded in-process import executor is simple and fast for a take-home. It is not a durable distributed queue. One backend instance owns sessions and interrupted-job recovery; multiple replicas need a different coordination/session design.

<!-- pagebreak -->

## 3. Data model and relationships

| Entity | Identity and relationship |
| --- | --- |
| user_account | Username; BCrypt hash and FINANCE/FOUNDER role. |
| mapping_version | Immutable partner/report mapping; optional predecessor links versions. |
| import_batch | UUID; unique partner + report type + SHA-256; references approved mapping. |
| source_row / row_error | Import has many source rows and errors; row number retains evidence location. |
| order_record | Unique partner + external order ID; source import/row; dates and monetary value. |
| movement | Unique partner + transaction ID; signed settlement, COD, freight or receipt record. |
| rate_card | Partner/kind and inclusive dates; commission, fees, settlement delay and version ID. |
| reconciliation_entry | One result per order; expected/received/risk, taxes, status and calculation JSON. |
| exception | One per order/type; amount, active state and opened date. |
| claim | Exception reference and immutable creation snapshot; notes, status and actor. |
| audit_event | Actor/action/entity/timestamp; claim and mapping history. |
| match_suggestion | Movement/order proposal; account, model, confidence and approval state. |

An import has many source rows; each normalized order/movement retains its source reference. Movements join orders by partner and external ID, deliberately allowing unmatched records. An order has one reconciliation result and many exception types; each exception can have historical claims, with at most one active claim. Audit records use entity type/ID rather than a universal foreign key.

Flyway V1 creates the workspace and preserves the three brief examples. V2 records the previewed mapping and actor; V3 supports incremental reconciliation; V4 persists match proposals and date indexes. Legacy schema.sql remains in the source but automatic SQL initialization is disabled. Existing foundation databases baseline at version 0.

Money is NUMERIC(14,2); percentage rates are NUMERIC(7,4). Raw and canonical JSON retain evidence, not financial authority. Indexes cover partner/order and transaction identity, movement matching, import/row lookup, mapping history, status and exceptions. A partial unique index protects active claims; the service also locks and checks the whole settlement exception family.

## 4. Import and mapping flow

CSV and values-only, single-sheet XLSX reports support orders, settlements, COD, freight and warehouse receipts. Parsing streams CSV records or XLSX SAX events and writes batches of 1,000 raw rows. Limits: 30 MB, 250,000 rows, 100 columns, 128-character headers and 4,096-character cells. XLSX formulas and multiple sheets are rejected. Required identifiers, ISO dates, decimal precision and nonnegative taxes are validated; settlement adjustments may be negative.

Import states are PARSING, AWAITING_MAPPING, PROCESSING, COMPLETE or FAILED. Two workers with an eight-job queue process uploads; counters, row errors and history are persisted. Interrupted work is marked failed on restart and can be retried. Originals remain available for evidence.

The user selects manual, saved or optional AI mappings, previews 20 canonical rows and explicitly approves. Approval must match the exact preview and actor. Approved mappings are saved per partner/report; rollback creates a new version. Preview is a sample: all rows are validated during processing.

<!-- pagebreak -->

## 5. Financial rules and rate cards

Production reconciliation uses set-based PostgreSQL NUMERIC arithmetic. Java BigDecimal reference rules use HALF_UP to two decimals. Commission is rounded before subtracting fees. Imported amounts cannot silently acquire extra fractional digits.

Delivered expected settlement = order value − rounded commission − fixed channel fee − TCS − TDS. Returned expected settlement = −return fee − TCS − TDS. Received = sum of signed settlement and COD movements through the as-of date. Refunds and negative adjustments are retained as signed movements; the model does not implement item-level partial returns.

TCS and TDS are legitimate reported deductions. They reduce expected cash and are summarized separately by partner; they are not automatically treated as leakage. No statutory tax rates are inferred. Correct source tax amounts remain an input assumption.

Absolute settlement differences at or below ₹10 are settled. Only gaps strictly greater than ₹10 become exceptions. A positive gap while the settlement is not yet due is pending; after the due date, zero received is unpaid past due and a partial receipt is short paid. Overpayment beyond tolerance or missing required rates needs manual review. Future-dated movements do not count yet.

Freight overcharge = actual freight minus expected flat courier charge, flagged only above ₹10. Missing return risk is the order value when a required warehouse receipt remains absent after the return deadline, subject to the same threshold. Default return deadline is order date + RETURN_GRACE_DAYS (14), unless return_due is supplied. This is a labelled demonstration assumption, not a carrier contract.

An order can have financial, freight and return exceptions simultaneously. Money at risk sums these actionable categories; it is an operational exposure indicator and may include overlapping commercial exposure, not a certified accounting loss. A single displayed status follows precedence, while detail retains every exception. Dashboard amounts come from these persisted results and active exceptions.

Rate selection uses partner, CHANNEL/COURIER kind and order date. Effective dates are inclusive. Backend writes acquire an advisory lock, reject overlapping periods and insert a version. Replacement shortens the prior period to the previous day; calculation breakdowns retain rate IDs. Direct database writes bypass the application overlap check and are outside the supported editing path.

Assumed seed cards: Website channel has ₹90 fixed fee, 0% commission, ₹96 return fee and 7 calendar days; Marketplace 1 has 20% commission, ₹60.20 fixed fee, ₹96 return fee and 14 days. Courier cards use ₹70 delivery/₹96 return flat charges. These are synthetic examples, not real commercial agreements. Original brief orders retain authoritative expected overrides, explicitly labelled in order evidence.

## 6. Duplicates, splits and reconciliation lifecycle

Identical file bytes are rejected per partner/report using SHA-256 even if renamed. Orders are unique per partner/order ID; transactions are unique per partner/transaction ID across movement kinds. Split settlements are valid when each installment has its own transaction ID, and are summed once. Existing-order imports are rejected rather than silently updating financial history; a correction/revision workflow is not implemented.

Normalization, reconciliation and completion commit together. Imports recalculate affected orders; rate edits recalculate their partner; a manual action and daily India-time refresh handle due-date changes. A global advisory lock protects conflicting financial operations. Unmatched movements stay visible and can be assigned only to a same-partner order through explicit approval.

<!-- pagebreak -->

## 7. AI integration and failure handling

AiProvider calls the real OpenAI Responses endpoint, https://api.openai.com/v1/responses, using a backend-only OPENAI_API_KEY and OPENAI_MODEL. It requests strict structured JSON and store=false. There are no hardcoded outputs presented as AI. The final audit fixed an accidental space in the endpoint that prevented application construction, and added a regression test.

Mapping prompts send headers and canonical field names only, not report rows. Headers are untrusted data and may still contain sensitive text if a user puts it in a header. Instructions explicitly prohibit following header content as commands or calculating money. Validation rejects unknown/duplicate fields and headers, incomplete coverage, invalid confidence and oversized reasons. Confidence below 0.85 or no header is labelled uncertain. Suggestions never approve an import.

Missing-ID matching first selects up to 20 same-partner candidates in SQL. AI sees opaque candidate tokens and redacted comparison facts rather than source identities or exact money. At most five validated proposals are retained. Approval checks the account, movement and target, pending state and 24-hour expiry. Manual matching remains available.

Transport limits include a 5-second connect timeout, 25-second full request/body deadline and 128 KiB response cap. HTTP errors, refusal, incomplete or malformed responses and validation failures produce an unavailable message with manual fallback. Local HTTP fixtures exercise the actual request/response code; they are not proof of provider availability.

Live-provider limitation: on 10 November 2026 a direct model-metadata request succeeded, but a real Responses request returned HTTP 429 `credit_balance_exhausted` / `insufficient_quota`. The in-app Suggest with AI call returned the same unavailable message, and manual mapping still completed and reconciled to ₹910 unpaid risk in the browser flow. Successful live model output therefore remains unverified. The isolated reviewer instance intentionally has AI credentials unset; reviewers can exercise the fallback without an API account.

## 8. Security, permissions and data handling

Spring Security authenticates two environment-provisioned demo users, stores BCrypt cost-12 hashes and uses server sessions. Cookies are HttpOnly, SameSite=Strict, with a 30-minute idle timeout; Secure is configurable for HTTPS. CSRF protects mutations, including login/logout. Both roles can import, approve mappings/matches and manage claims. Only the founder can write rate cards and restore mapping versions. These restrictions are enforced at backend endpoints, not only in UI controls.

The demo is one brand workspace. It does not provide tenant isolation, self-service registration, MFA, password recovery, distributed sessions, an independently verified security audit or comprehensive login rate limiting. Production needs HTTPS, managed identity, secret rotation, backups, retention controls and operational monitoring.

Only synthetic samples are supplied. Original uploads and raw rows are retained for evidence; there is no automated personal-data redaction or deletion/retention workflow. Users must not upload real customer information into this demo. Mapping sends headers externally; matching sends redacted comparisons. API keys are never required in frontend environment variables or in submission documents.

Local .env files, private keys, uploads, node_modules, target/dist, logs, browser artifacts and .local are excluded by ignore rules. The submission ZIP uses an explicit two-file allowlist. Because the supplied folder has no Git metadata, this audit cannot certify previously committed secrets or remote history. The candidate must publish only reviewed source and verify the remote contents.

<!-- pagebreak -->

## 9. Claims, evidence and interface

Claims are created from active exceptions with a note and an immutable snapshot of order details, source records and calculation breakdown. Active duplicate settlement claims remain blocked even if an unpaid order becomes short-paid after a partial payment. Workflow is OPEN → IN_REVIEW → RESOLVED or REJECTED; closed claims are immutable. Notes and transitions are audited. Evidence exports JSON with snapshot and history. No email, partner API call or external claim submission is performed.

Main screens are sign-in, overview, import centre, mapping review, reconciliation, order evidence and claims. Rate cards and unmatched review are supporting screens. Neutral colors, one green accent, Indian currency formatting and explicit actions aim to make financial review readable. Search, partner/type/status filters, pagination and loading/error/empty states expose real database data. Mobile navigation and overflow are exercised by browser tests; a comprehensive assistive-technology audit has not been conducted.

## 10. Verification evidence

Verification rerun on 10 November 2026 (IST), against the current workspace: backend Maven test passed 32 tests with 0 failures, 0 errors and 0 skips, including 11 real PostgreSQL 16.15 integration tests, in 24.588 seconds using Java 17.0.21. A separate run without TEST_DATABASE_URL skipped exactly those 11 tests, confirming that the integration class is database-gated rather than silently passing. Frontend TypeScript/Vite production build passed with 38 modules; JavaScript was 184.82 kB (57.44 kB gzip) and CSS 7.02 kB (2.20 kB gzip).

Docker Compose configuration validation, image rebuild and isolated stack startup passed; PostgreSQL, backend and frontend reported healthy and /api/health returned UP. All three Playwright workflows passed on a clean reviewer stack: both roles; HttpOnly/SameSite=Strict session cookie; upload, mapping, 20-row preview, approval and completion; ₹910 order evidence; claim creation, IN_REVIEW update and evidence JSON download; founder rate version on mobile with no horizontal overflow; and filters plus manual match through the production API. The XLSX loader scenario covered five report types and produced 40 orders, five deliberate rejected rows, six statuses and ₹5,992 risk.

Current audit also inspected backend auth, importing, mapping, rate edits, SQL reconciliation, dashboard aggregation, claims/evidence and provider failure paths. This is code inspection plus the tests listed above, not a claim that every possible input or UI action was exercised.

Historical evidence, retained separately: 4 November backend suite had 31 passing tests including 11 PostgreSQL integration tests; three production browser workflows passed; CSV/XLSX scenarios covered six statuses, duplicates and five intentionally invalid rows. These historical results must not be confused with the current rerun. Raw reports and validation notes are in docs/validation and docs/VALIDATION.md.

The current source uses the production OpenAI Responses endpoint, and offline HTTP fixtures exercise the actual transport, validation and failure path. A setup script provisions a separate Compose project and synthetic accounts, leaving existing root credentials and data untouched.

## 11. Performance evidence and reproducibility

A fresh 10 November Docker measurement on the isolated reviewer stack processed 50,000 order rows plus 50,000 settlement rows (100,000 total) in 22.443 seconds: 13.497 + 8.946 seconds per file. It began with 86 existing records, accepted every row and reconciled after both files. The retained 4 November run measured 25.952 seconds for the same 100,000-row shape; a larger 200,000-total-row Docker run took 80.079 seconds and missed one minute. The 200,000-row result was not rerun and none of these figures are throughput guarantees.

Environment: Windows 11 build 26200, AMD64 Family 25 Model 80, 12 logical processors, about 7.3 GiB usable RAM, Python 3.13.2, Docker 29.6.2, Java 17.0.21 and PostgreSQL 16.15. Backend heap was 384 MB with two active JVM processors; backend/database/frontend container caps were 768/512/128 MB. Other workstation services were present.

Timing includes HTTP multipart upload, hashing, streaming parse, PostgreSQL raw rows, 20-row preview, approval, validation, normalization, reconciliation after both files and polling. It excludes generation, human review delay, dependency installation and cold startup. This is an HTTP/database end-to-end CSV benchmark, not an in-memory microbenchmark. Large XLSX and simultaneous-user throughput remain unmeasured.

Reproduce against a running local app: python scripts/benchmark.py --base http://127.0.0.1:16173 --rows 50000. The option is rows per file, so this means 100,000 total. It preserves prior records and writes a JSON report. Run separately from builds/browser tests. Current evidence is .local/submission-audit/benchmark-2026-11-10.json (outside the ZIP); historical JSON is in docs/validation/.

## 12. Assumptions, limitations and next steps

Assumptions: one brand and INR; case-sensitive partner names; calendar-day settlement schedules; flat courier fees; report-provided tax deductions; complete transaction identities for duplicate protection; one warehouse receipt settles the return obligation. The three original examples use expected overrides to preserve the assignment values, rather than claiming those expectations were derived from the illustrative cards.

Known scope cuts: no bank-holiday/working-day schedules, weight/zone courier slabs, fee GST engine, item-level partial refunds, source correction versions, automatic partner connectors or live claim submission. Report upload is the integration boundary. Claims exports are JSON, not a partner-specific claim PDF. No external deployment has been established.

Operational limitations: single-instance sessions/job recovery, no durable queue, no object-store retention policy, no tested production backup/restore procedure, and no broad concurrency/security/accessibility certification. Overlap checks rely on the service locking protocol. Large retained databases and simultaneous native/Docker processes can exhaust this workstation's memory; use one application stack and sequential heavy checks.

AI success still needs a funded/authorized provider account and a real response check. Manual mapping and matching are the supported fallback. The 100,000-total-row CSV target passed in both the current and historical runs, while the 200,000-row Docker stress target did not. Do not extrapolate either result to concurrent workloads or large XLSX.

Priority next steps: publish the reviewed source to the supplied repository; verify an anonymous clone; run a funded AI smoke test; add partner identifiers and correction imports; harden identity/rate limiting and personal-data retention; add durable workers/shared sessions if scaling; deploy behind HTTPS and verify operational backups before real use.

## 13. Reviewer handoff

Use deployment.md for exact commands and the isolated reviewer accounts. The reviewer Compose environment uses new named volumes, a locally generated private database password and blank provider credentials. It does not overwrite the candidate's existing .env or retained application data.

The submission archive deliberately contains only this PDF and deployment.md. Editable Markdown, source code, benchmark reports, private environment files, videos and dependencies are outside the ZIP. Source code must be available at the GitHub URL before reviewers can reproduce the setup. The public repository was still empty at the 10 November API check; publishing the source and verifying an anonymous clone were not performed by this preparation task.

Document basis: current Java/TypeScript/SQL/configuration inspection, the 10 November verification recorded above, current and dated local raw benchmark/provider evidence, and a direct GitHub API visibility check. No GitHub, deployment or performance URL has been invented.
