# Validation report

Updated: **5 November 2026**, Asia/Kolkata. Full regression/build/performance results below are from 4 November; the live-provider attempt and manual fallback check are from 5 November.

## Current verification addendum — 10 November 2026

A current-source rerun produced 32 backend tests with 0 failures/errors/skips, including 11 PostgreSQL 16.15 integration tests, in 24.588 seconds with Java 17.0.21. The frontend production build passed with 38 modules (JavaScript 184.82 kB / 57.44 kB gzip; CSS 7.02 kB). The isolated reviewer Docker stack rebuilt and reached healthy state at http://127.0.0.1:16173, and all three Playwright workflows passed on a clean stack. The XLSX loader scenario produced 40 orders, five deliberate row errors, six statuses and INR 5,992 risk across five report types. A fresh 100,000-total-row Docker benchmark took 22.443 seconds (13.497 + 8.946). A live OpenAI retry still returned HTTP 429 `credit_balance_exhausted`; the application returned the unavailable message and manual mapping remained usable. The detailed current summary is in `docs/submission/Technical_Design_Document.md`.

## Live provider attempt — 5 November

The user configured a private API key and `gpt-6-luna`. The existing Docker backend was recreated with those environment values and all three services became healthy. Authenticated `/api/ai/status` confirmed configuration. A real mapping request through the application returned unavailable after 3.468 seconds, with no fabricated suggestions. A minimal diagnostic request to the fixed official Responses endpoint returned HTTP **429**, code **credit_balance_exhausted**, type **insufficient_quota**. No API key or raw authorization/error content was recorded.

OpenAI's [error-code documentation](https://developers.openai.com/api/docs/guides/error-codes) identifies this code as an exhausted API credit balance and directs the account owner to add credits. Further provider requests were stopped. Successful live mappings and matches remain unverified; the rejection does not establish model access or output quality.

The retained synthetic order report then passed manual preview/approval, accepted one row with zero errors and reconciled to UNPAID_PAST_DUE with INR 910.00 at risk. Health remained UP. [Sanitized live attempt and fallback evidence](validation/live-ai-attempt-2026-11-05.json) records the actual outcome. The new opt-in `scripts/verify-live-ai.py` uses two provider calls on a successful run and checks that mapping and matching require explicit approval. Only its mapping-failure path has run so far; later matching/success assertions remain pending credits. Python syntax compilation passed. No backend/frontend source changed during this check, so the prior full regression results were not rerun or relabelled as new results.

## Completed checks

| Check | Actual result |
| --- | --- |
| Final complete Java 17 backend package | PASS - 31 tests, 0 failures, 0 errors, 0 skipped; Maven package completed in 1 min 16 s using the Maven container and dedicated test database. |
| Real PostgreSQL integration tests | PASS - 11 tests against isolated PostgreSQL 16.15, included in the 31-test result. |
| Frontend TypeScript / Vite production build | PASS - 38 modules; JS 184.82 kB before gzip / 57.44 kB gzip, CSS 7.02 kB. |
| Docker backend/frontend image builds | PASS - Java 17 and Node production builds completed. Image build ran 20 unit/parser/domain/AI tests; its 11 integration tests were skipped and were run separately in the complete suite above. |
| Docker Compose startup | PASS - database, backend and frontend healthy; frontend-to-backend health probe passed. Existing stack also restarted successfully with retained volumes during final handoff. |
| Production browser workflows | PASS - 3 Playwright tests against http://127.0.0.1:15173, total 29.0 s. Finance 8.4 s, mobile founder 2.7 s, filters/manual-match 6.8 s, plus runner/browser overhead. |
| Native and Docker all-report XLSX scenarios | PASS - each route accepted 40 orders, retained expected row errors, produced six reconciliation statuses and INR 5,992 risk. |
| Docker complete HTTP target-size benchmark | PASS - 50,000 orders + 50,000 settlements (100,000 total rows) in 25.952 s; all accepted, zero errors, all benchmark orders SETTLED. |
| Docker larger HTTP stress benchmark | 100,000 orders + 100,000 settlements (200,000 total rows) in 80.079 s; all accepted/settled. Combined stress workload exceeded one minute; each individual 100,000-row file finished within one minute. |
| Earlier native HTTP benchmark | PASS - 200,000 total rows in 52.516 s on native PostgreSQL 17.10 / Java 17 with a different resource budget; retained as a separate observation. |
| Python utility syntax | PASS - generator, loader and benchmark compiled with py_compile. |
| Live OpenAI provider request | ATTEMPTED 5 Nov — HTTP 429 credit_balance_exhausted / insufficient_quota. Credentials loaded; successful live output remains unverified pending API credits. Manual fallback after the actual rejection passed. |
| Publishing / external claims | No publishing, Git push or claim submission was performed. |

Final authenticated [handoff verification](validation/final-verification-2026-11-04.json) passed: health UP, all three original sample amounts preserved, original sample risk ₹1,492.00, and dashboard risk equal to its exception breakdown. The recorded native-data scan is an earlier read-only verification; the final handoff check uses Docker.

The earlier native JDK was Temurin 17.0.21+9. The final complete test suite used Java 17.0.20.1 in the Maven image; production runtime used Java 17.0.21. Native frontend tooling was Node 22.14.0 / npm 10.9.2. Docker validation used Docker Engine 29.6.2, Compose v5.3.1, Java 17.0.21 and PostgreSQL 16.15. Docker ports 15173/18080 were used because the native development ports and another existing port were occupied.

## Test coverage

The final suite consists of 20 domain/parser/provider/AI validation tests plus 11 PostgreSQL integration tests. [Sanitized final test summary](validation/completion-tests-2026-11-04.json) preserves per-suite counts without environment secrets.

- Financial rules: HALF_UP rounding, negative return adjustments/refunds, due-today behavior, positive and negative ₹10 boundaries, explicit TCS/TDS and rate validation.
- Parsing/validation: quoted/BOM CSV, duplicate headers, inconsistent row widths, values-only XLSX, formula rejection, invalid amounts/dates, required mappings and native Excel TRUE/FALSE normalization.
- AI: schema/headers/fields, confidence bounds, malformed/untrusted/incomplete/duplicate output, uncertainty and unavailable fallback. Actual local HTTP wire tests verify authorization/store=false/schema, refusal, HTTP failures, whole-body timeout and response-size bound. Match tests cover redaction, no automatic linking, account/target binding, expiry, supersession and explicit approval.
- End-to-end integration: upload → source storage → preview → approval → duplicate handling → split receipts → reconciliation → claim creation/update/export and dashboard totals.
- Permissions/session: anonymous and outsider denial, finance rate-write denial, founder actions, CSRF rejection, login and session invalidation on logout.
- Claims/mappings/rates: concurrent claim attempts produce one success and one conflict; an active settlement claim also blocks a replacement after unpaid becomes short-paid. Independent freight claims remain possible. Closed claims and creation evidence stay immutable. Mapping rollback permissions, inclusive rate dates, overlap rejection and period replacement are covered.
- Exceptions: freight exactly ₹10 versus ₹10.01, missing/received warehouse returns, legitimate tax deductions on returned orders, unmatched manual approval and missing rate-card review.
- Temporal correctness: future-dated cash, tax, freight and warehouse receipts stay out of current received/risk calculations.
- Incremental correctness: importing COD for one order leaves an unrelated order’s reconciliation timestamp, risk and active exception intact.
- Inspection/recovery: import/claim/dashboard filters, unmatched pagination beyond the former 200-row cap, restart preservation of current-day snapshots, stale-result refresh and interrupted-import recovery.

The original DomainChecks remains a 12-assertion domain check, called by one unit test. Its in-memory 100,000-row aggregation timing is not used to claim upload/database performance.

## Browser verification

The final three tests ran against the built nginx/frontend/backend/database stack:

1. Finance sign-in; HttpOnly/SameSite cookie checks; CSV upload; unconfigured-AI fallback; validated preview and approval; inspection of expected/risk values; claim creation and status note; evidence attachment download; sign-out; no captured page errors.
2. Co-founder sign-in at 390 × 844; rate version creation; navigation back to the dashboard; no document-wide horizontal overflow.
3. Dashboard partner filtering; import search/type/state filters; missing-ID COD upload; honest unconfigured-AI fallback; explicit manual match; refreshed SETTLED order and empty claims-search state.

Desktop dashboard/claim and mobile dashboard screenshots were saved under ignored .local/qa and visually inspected. These checks exercise labels, roles, state transitions and responsive layout. They are not a comprehensive WCAG audit or production browser compatibility certification.

## Synthetic XLSX scenarios

Both native and Docker flows accepted/rejected the same records:

| Report | Accepted | Rejected |
| --- | ---: | ---: |
| ORDERS | 40 | 3 |
| SETTLEMENTS | 41 | 2 |
| COD | 1 | 0 |
| FREIGHT | 4 | 0 |
| RETURNS | 2 | 0 |

The rejected rows deliberately cover malformed values/dates, duplicate identities and invalid tax data. Accepted data covers successful and split settlements, a ₹10 boundary, past-due/partial COD, signed return fees, freight overcharge, pending settlement, missing and received returns, legitimate tax deductions and an unmatched ID.

The verified 40-order prefix totals ₹5,992.00 money at risk and contains SETTLED, SHORT_PAID, UNPAID_PAST_DUE, PENDING, FREIGHT_OVERCHARGED and RETURN_NOT_RECEIVED. It is independent of the preserved three seeds and the benchmark datasets.

Raw summaries: [native XLSX result](validation/xlsx-scenarios-2026-11-04.json), [Docker XLSX result](validation/xlsx-scenarios-docker-2026-11-04.json).

## End-to-end performance

The benchmark uses equal-sized order and settlement CSV files. `--rows` specifies rows per file, so 50,000 produces 100,000 total input rows and 100,000 produces a 200,000-row stress workload. It authenticates through the same HTTP application flow and verifies all generated orders are SETTLED. Each run creates uniquely prefixed synthetic records and retains previous data.

| Run | Runtime / previous orders | Orders file | Settlements file | Total / 200,000 rows |
| --- | --- | ---: | ---: | ---: |
| 3 Nov, initial foundation flow | Java 23.0.1; 3 prior orders | 19.486 s | 19.222 s | 38.708 s |
| 4 Nov, before incremental work | Java 17.0.21; 100,046 prior orders | 43.543 s | 65.781 s | 109.336 s |
| 4 Nov, incremental recalculation | Java 17.0.21; 200,046 prior orders | 30.853 s | 34.216 s | 65.081 s |
| **4 Nov, final batching and bounded JVM** | **Java 17.0.21; 300,046 prior orders** | **35.779 s** | **16.737 s** | **52.516 s** |

The final native run met one minute for the two files’ combined 200,000 rows. All rows were accepted, there were zero row errors, and all 100,000 new orders were SETTLED. The final application had a 512 MB maximum Java heap and four active JVM processors. The host had 12 logical processors and approximately 7.3 GiB usable RAM.

Measured native environment: Windows 11 build 26200, AMD64 Family 25 Model 80, Python 3.13.2, Temurin 17.0.21 and PostgreSQL 17.10. Docker Desktop and other existing services were present; this is a local workstation measurement, not an isolated production load lab.

Included: HTTP multipart upload, SHA-256, streaming parsing, raw-row database storage, 20-row mapping preview, approval requests, row validation, duplicate checks, normalized inserts, reconciliation after both files and HTTP polling.

Excluded: dataset generation, human review delay, dependency/image installation, cold JVM startup and independent final verification requests.

The slower runs are retained because the database grew and the processing strategy changed. They are observations rather than a controlled hardware A/B experiment. Incremental reconciliation avoids rewriting unrelated history; set-based page updates reduce per-row SQL overhead. Results remain dependent on data shape, disk/cache state, database size and concurrent services.

### Final Docker measurements

| Workload | Previous orders | Order file | Settlement file | Combined |
| --- | ---: | ---: | ---: | ---: |
| 200,000 total rows (100,000 per file) | 48 | 36.821 s | 43.252 s | **80.079 s** |
| 100,000 total rows (50,000 per file) | 100,048 | 13.692 s | 12.260 s | **25.952 s** |

Both runs accepted every row with no row errors and independently verified that every new order was SETTLED. They used nginx -> Java 17.0.21 -> PostgreSQL 16.15 in Docker Desktop on the same Windows host. The backend used a 384 MB maximum heap and two active JVM processors; backend/database/frontend container limits were 768/512/128 MB. The target-size run followed a Docker Desktop restart with volumes retained; cold application startup is excluded from its timer. The resulting Docker workspace has 150,048 orders.

The requested 100,000-total-row workload passed. The larger combined 200,000-row Docker workload missed one minute, although each 100,000-row report was below one minute. Do not describe the native 52.516-second result as a Docker result or the smaller run as 100,000 orders plus settlements. These are separate observations with different workload sizes and resource budgets, not a controlled comparison or a throughput guarantee.

An initial attempt at the final target-size run could not connect because Docker Desktop was stopped; no timing or processing success is claimed for that attempt. The engine and only this Compose project were restarted before the recorded passing run.

Raw reports:

- [Initial Java 23 result](validation/benchmark-2026-11-03-java23.json)
- [Java 17 before incremental recalculation](validation/benchmark-2026-11-04-before-incremental.json)
- [Incremental result](validation/benchmark-2026-11-04-incremental.json)
- [Final native Java 17 result](validation/benchmark-2026-11-04-final.json)
- [Docker 200,000-row stress result](validation/benchmark-docker-200k-2026-11-04.json)
- [Docker 100,000-total-row result](validation/benchmark-docker-100k-2026-11-04.json)

Reproduce with `python scripts/benchmark.py --base http://127.0.0.1:15173 --rows 50000` for 100,000 total rows, or `--rows 100000` for the 200,000-row stress run, after starting the application. The script records actual runtime versions and prior record count. Run it without simultaneous builds, browser tests or other imports.

## Resolved verification blockers and limits

The initial app’s missing BCrypt encoder prevented valid sign-in; the encoder/session implementation is now tested. Integration/browser checks also exposed SQL assembly and incompatible saved-header defaults, which were fixed and covered by the final checks.

During verification, overlapping unrestricted Java processes exhausted native memory on the workstation. No passing result is claimed for that aborted attempt. Maven/test/runtime heaps and Compose service memory limits were then added; the bounded builds, benchmark and container/browser checks passed with those bounds.

The PowerShell Docker helper now judges native-command exit codes rather than treating normal stderr progress as a fatal error. An occupied host port was avoided using verified free validation ports. No unrelated containers or installed PostgreSQL service were stopped.

During final hand-back, a native restart with the large retained database and Docker Desktop active reached Windows' paging-file limit. The native PostgreSQL refresh also reported a temporary file-descriptor read error. After a clean database restart, a read-only scan of all **400,046** orders and reconciliation monetary/breakdown data succeeded. The native database was then cleanly stopped. The hand-back uses one healthy Docker stack at **http://127.0.0.1:15173**; native Java/Vite/PostgreSQL duplicates are stopped and their data retained. Running both environments together on this workstation is not treated as a reliable configuration.

Remaining validation boundaries:

- Live AI output and model access remain unverified: a key is now configured, but the provider returned credit_balance_exhausted. Mapping and human-approved proposals, local wire tests and manual fallback are implemented.
- Large performance measurements use CSV. Docker/PostgreSQL 16.15 has now been benchmarked at both 100,000 and 200,000 total rows; large XLSX and concurrent-load performance remain unverified.
- Concurrent large imports, multi-instance recovery/shared sessions, long-running production resilience, bank-holiday schedules and courier weight/zone contracts require further work.
- The current import contract rejects existing order identities rather than offering source correction/revision. Manual and AI-proposed missing-ID matches both require explicit human approval.
- This is a synthetic-data result. No real personal data, live claims submission or external publication was used.
