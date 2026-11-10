# Work checkpoint - 5 November 2026

## Completed recorded walkthrough - 5 November

- User requested a Playwright walkthrough video with explanation. Created a real 208.4-second recording at 1440 x 1000 with 23 Hinglish-captioned chapters, highlighted clicks, a local player, speaking notes and evidence download. No audio track or provider requests.
- Final player: `.local/walkthrough/2026-11-05-37b46d/WATCH.html`. Portable archive: `.local/walkthrough/Reconflow-Hinglish-Walkthrough-2026-11-05.zip`.
- Verified three accepted orders, one invalid order rejection, four accepted settlement transactions, one duplicate rejection, INR 37 risk, settled split/tax examples and an IN_REVIEW claim with exported evidence. No page errors. Playback, chapters, play/pause/speed and ZIP integrity passed; key frames visually inspected.
- Reproduction scripts: `frontend/scripts/record-walkthrough.mjs` and `verify-walkthrough.mjs`; guide: `docs/WALKTHROUGH.md`; sanitized summary: `docs/validation/walkthrough-2026-11-05.json`.
- The first recording stopped on a recorder selector and had caption styles blocked by CSP. Corrected the selector and used bypassCSP only in the recording browser. Application source/server security remain unchanged. Both attempts' unique synthetic datasets and local artifacts are preserved; final delivery uses only the passing recording.

## Latest AI verification attempt - 5 November

- User configured a private API key and model `gpt-6-luna` in `.env`. Preserve those values and never print the key. The previous missing-key condition is superseded.
- Started Docker Desktop, recreated only the Reconflow backend to load the environment, and started project `reconflow-validation` on 15173/18080 with retained volumes. All three services healthy.
- Live mapping was unavailable. One minimal diagnostic request to the official Responses API returned HTTP 429, `credit_balance_exhausted`, type `insufficient_quota`. No further provider calls should be attempted until credits are restored. Do not claim successful live AI or verified model access.
- The retained synthetic report completed through manual preview/approval: one accepted order, zero errors, UNPAID_PAST_DUE and INR 910.00 risk. Docker workspace now has 150,049 orders; prior data preserved.
- Added opt-in `scripts/verify-live-ai.py` for live mapping and explicitly approved matching. Failure path ran; success/matching assertions remain unexecuted pending credits. Syntax compiled. Sanitized outcome: `docs/validation/live-ai-attempt-2026-11-05.json`.
- Updated validation, coverage and deployment docs. Backend/frontend source unchanged; 4 November tests/build/benchmark remain the latest full results. No push, publication or external submission.

## Prior completed implementation - 4 November

## Current completion status

The user resumed and requested completion. Historical 3 November pause notes below are retained as history, not current blockers or instructions to stop.

- Complete backend package passed **31 tests**, including **11 real PostgreSQL 16.15 integration tests**, with no failures/errors/skips. Tests used Java 17.0.20.1 in the Maven image; production runs Java 17.0.21.
- Frontend production build passed (38 modules, JS 184.82 kB / 57.44 kB gzip, CSS 7.02 kB). **Three** production Playwright workflows passed in **29.0 seconds**.
- Docker image builds passed (20 non-database tests; 11 integration tests intentionally skipped there and run separately above). All three services started healthy, including a final retained-volume restart after Docker Desktop had stopped.
- **Application: http://127.0.0.1:15173**, backend 18080, Compose project **reconflow-validation**. Preserve its database/upload volumes. Docker workspace contains **150,048 orders** after browser fixtures and benchmarks.
- All five XLSX report types passed on native and Docker routes: 40 orders, five intentional row errors, six statuses and INR 5,992.00 scenario risk. Original three client examples remain unchanged.
- Imports reconcile affected identities, rate edits refresh their partner, and approved matches refresh their order. Full explicit/daily refresh remains available; startup only globally refreshes missing or stale reconciliation snapshots. Interrupted jobs remain retryable. V3 indexes imports; V4 stores match proposals and indexes as-of dates.
- Database-backed filters/search/pagination now cover import history, orders, claims, unmatched movements and target-order selection. Dashboard partner scopes totals, taxes, exceptions, age, unmatched and claims consistently. Removed the old 200-unmatched visibility cap.
- Active settlement claims are protected across UNPAID_PAST_DUE -> SHORT_PAID classification changes and concurrent creation; independent freight/return claims remain possible. Immutable evidence and status/audit history are tested.
- Genuine AI mapping and missing-ID proposals use a shared bounded Responses transport. Mapping sends headers only; matching sends opaque tokens and redacted comparison facts. Stored proposals require explicit creating-account approval within 24 hours. No automatic linking or fabricated AI fallback. Real local HTTP fixture tests exercise the wire contract, timeout and oversized/malformed/refused responses.
- **User has no API key. Live provider success/quality remains unverified.** Manual mapping and matching complete the core flow. Do not request the same key again or represent fixture output as live AI.
- Docker target-size HTTP benchmark: **50,000 orders + 50,000 settlements = 100,000 input rows in 25.952 seconds**, all accepted/settled, zero errors, 100,048 existing orders. Java heap 384 MB, two active processors, PostgreSQL 16.15.
- Docker stress benchmark: **100,000 orders + 100,000 settlements = 200,000 input rows in 80.079 seconds** (36.821 / 43.252 per file). Combined workload missed one minute. Earlier native 200,000-input-row result remains **52.516 seconds**, with different resource settings; do not conflate these results.
- README, deployment, technical design source, assumptions, validation and requirement-coverage matrix describe actual implementation, rates/schedules, evidence and limits. Sanitized raw results are in docs/validation.
- Native database retains **400,046 orders**; a clean-restart read-only monetary/breakdown scan passed after an earlier memory/page-file/I/O failure. Native Java/Vite/PostgreSQL remain stopped. Use only one runtime on this approximately 7.3 GiB machine; do not stop unrelated containers/services.
- No Git repository was initialized. No push, publication or external claim submission occurred. Existing source, original examples and prior datasets were preserved.
- Final authenticated handoff check passed: health UP, original sample amounts unchanged, dashboard risk equal to summed exceptions, documentation links valid, and all three Docker services healthy. Raw summary: docs/validation/final-verification-2026-11-04.json.

For further changes, inspect current files/processes first and follow deployment.md. The remaining sections record the earlier pause and are historical; their tool availability, test counts and pending-work lists are superseded by this section and VALIDATION.md.

## Historical checkpoint - 3 November 2026

User explicitly requested stopping for today and resuming tomorrow. Do not treat this checkpoint as a request to start background work or schedule an automation.

## Workspace and preservation

- Project root: `Reconflow_Foundation/reconflow`, containing `backend`, `frontend`, and `compose.yaml`.
- The extracted workspace has no Git metadata. Preserve all existing files and changes; do not initialize Git, reset, push, publish, or submit without the user's instruction.
- No applicable AGENTS.md files were found during inspection.
- Original three sample records and their authoritative expected values were preserved. Legacy `demo_order` remains intact.
- Local secrets are in the ignored root `.env`. Do not print or commit them.

## Implemented source

- Fixed original BCrypt encoder registration defect; successfully ran the original app through the frontend proxy.
- Flyway V1 data model and V2 mapping-preview approval fields. Existing installations baseline at version 0 without dropping the legacy table.
- PostgreSQL user accounts, BCrypt hashes, cookie sessions, CSRF, logout, backend roles. Both roles operate imports/reconciliation/claims; founder alone edits rates and restores mapping versions.
- CSV and single-sheet XLSX streaming/event parsing, file/row limits, source file hashes and storage, raw row evidence, asynchronous progress, validation errors, import history, retry, duplicate file and transaction detection.
- Manual mapping, saved versions, 20-row preview, approval, rollback as a new version. Latest source also binds approval to the mapping previewed by that actor.
- Dated channel/courier rate cards, replacement of an existing period, overlap rejection through serialized application transactions.
- Set-based PostgreSQL reconciliation using NUMERIC; Java financial domain uses BigDecimal. Split receipts, taxes, refunds, pending/manual states, freight and missing-return exceptions; manual approval of unmatched records.
- Claims with unique active-claim constraint, immutable creation evidence, notes, status history, JSON export; no external submission.
- Database-backed dashboard and React screens for login, overview, imports, mapping, reconciliation, order evidence, claims, and rates; responsive neutral/green styling.
- Real backend OpenAI Responses API mapping integration using strict structured outputs and additional validation. Sends headers only. Missing key/model shows an explicit manual fallback; no fabricated AI suggestions.
- Complete Compose source with frontend/nginx, backend Java 17 image and PostgreSQL 16, persistent upload/database volumes, health checks. Docker is absent on this machine.
- Generators, HTTP benchmark, demo loader, Windows startup scripts, Playwright tests.

## Actual verification completed

- Original domain checks: 12 checks passed.
- Latest completed backend regression run: **19 tests, zero failures/errors**, including five real PostgreSQL integration tests. Coverage includes upload through claims, CSV/XLSX, duplicates, split payments, taxes, refund signs, freight ₹10 boundaries, missing returns/receipts, manual matching, missing rates, preview approval enforcement, roles/CSRF, mapping rollback, rate date overlap/replacement and dashboard totals.
- Backend tests ran on Java 23.0.1 with Java 17 compilation target against an isolated PostgreSQL 17.10 test database.
- Frontend TypeScript and Vite production build passed before the most recent small mapping-review change. Run a final build after resuming.
- **Two Playwright browser workflows passed** in installed headless Chrome: finance upload → mapping → reconciliation → claim → status update → evidence download → logout, and mobile founder rate creation/navigation. Last successful run: 32.0 seconds.
- Browser testing exposed and fixed saved mappings retaining optional columns missing from the next file. Screenshots were visually inspected; they are in `.local/qa/`.
- Real HTTP benchmark: **100,000 order rows + 100,000 settlement rows, all accepted, zero errors, 38.708 seconds total**. Orders took 19.486 s; settlements took 19.222 s. Includes upload, SHA-256, parsing, raw DB storage, preview/approval requests, validation, normalized records, reconciliation after both files and polling. Excludes dataset generation, human review time and JVM startup. This was measured before the final small source changes.
- Benchmark environment: Windows 11 build 26200, Python 3.13.2, Java 23.0.1 targeting 17, PostgreSQL 17.10, 12 logical processors, approximately 7.3 GiB usable RAM. Raw result: `.local/benchmark-result.json`.
- OpenAI integration design checked against official structured-output documentation: https://developers.openai.com/api/docs/guides/structured-outputs . Live provider invocation has NOT been tested; no key/model configured.
- Docker command check confirmed `docker` is unavailable. No Docker build/startup success is claimed.

## Important latest changes still needing final verification

After the latest passing backend run, small changes were made to:

- Subtract legitimate TCS/TDS from returned-order expected settlements too, with an added RETURNTAX regression case.
- Add authenticated `/api/system-info`; benchmark now reads actual Java/PostgreSQL versions and verifies all benchmark orders have SETTLED status.
- Remove the 200-source-row cap from order evidence and render audit details as JSON text.

Some of these were already covered in the latest suite, but run the whole suite on Java 17 to establish the final state. The last live backend JAR was older than the final source; rebuild before restarting.

## Local tools and data

- Maven: `C:/Users/aarya/Downloads/apache-maven-3.9.16-bin/apache-maven-3.9.16/bin/mvn.cmd`.
- Maven repository used: project `.local/m2`.
- Portable PostgreSQL bin: `C:/Users/aarya/Downloads/postgresql-17.10-2-windows-x64-binaries/pgsql/bin`.
- Project-owned PostgreSQL cluster: `.local/postgres`, port 55432, databases `reconflow` and `reconflow_test`, user `reconflow`. Password comes from root `.env` (also stored in ignored `.local/db-password` from initialization).
- The installed system PostgreSQL service was NOT modified.
- Portable Java 17 download and extraction completed: `.local/jdk17/jdk-17.0.21+9`. It has not yet been executed/tested.
- Existing system Java is 23.0.1. Node 22.14.0, npm 10.9.2.
- Frontend dependencies and Chrome are available. Playwright config discovers installed Chrome/Edge or accepts `PLAYWRIGHT_EXECUTABLE_PATH`.
- Database contains preserved seeds, the successful 100,000-order benchmark and synthetic browser-test records/claims. Do not delete these without authorization. Benchmark files/JARs/logs/screenshots are ignored in `.local`; uploads are ignored in `backend/uploads`.
- Windows sandbox prevented network/socket access and some dependency file reads. Download/build/run/browser/localhost commands worked with `require_escalated` auto-review. No approval rejection occurred.

## Shutdown state

At the user's stopping request, the project backend and Vite sessions were stopped. The isolated PostgreSQL cluster was cleanly stopped with pg_ctl. Source and all database files remain in place. No implementation work should continue until the user resumes.

## Suggested next work after resumption

1. Read this checkpoint and inspect any new user edits before modifying files.
2. Start the isolated PostgreSQL instance; use `scripts/start-database.ps1` (new script, still needs validation). Root `.env` DATABASE_URL uses port 55432; enable `?reWriteBatchedInserts=true` for the benchmark.
3. Select project-local Java 17 via process-only JAVA_HOME/PATH. Run all backend tests with TEST_DATABASE_URL pointing to `jdbc:postgresql://127.0.0.1:55432/reconflow_test` and TEST_DATABASE_PASSWORD from `.env`. Maven uses project `.local/m2`.
4. Package and start the final backend, applying V2 to the workspace database. Use a copied runtime JAR outside target so Windows does not lock the next build output. `scripts/start-backend.ps1` automates this but is newly added and needs validation.
5. Start frontend, rerun production build and browser workflows against the final Java 17 backend.
6. Run `python scripts/load-demo.py --xlsx` to exercise all five report types with intentional errors; verify the script's expected 40-order risk total of ₹5,992. This loader has NOT run yet.
7. If appropriate, rerun the benchmark on Java 17 and save final reproducible results. The modified benchmark requires the new `/api/system-info` endpoint.
8. Validate startup scripts and Compose source. `scripts/check-compose.ps1` was added but cannot run container startup here without Docker.
9. Finish README.md, deployment.md, docs/DECISIONS.md and Technical Design Document source (Markdown is appropriate). **These documents still contain the old foundation text; documentation work is pending.** Include architecture/schema/flows, rate and return assumptions, UI decisions, AI behavior, evidence, actual tests/timings, tradeoffs and limitations.
10. Record remaining limitations candidly: live AI and Docker runtime unverified; flat courier fees rather than weight/zone tiers; no automatic external claim submission; single-instance in-memory session/job executor (restart requires sign-in; interrupted jobs can be retried); existing order identities are rejected rather than revised by later imports; unmatched matching is human-driven rather than AI-generated.

The user's full requirement list is in the conversation. Do not represent every requirement as finished: core implementation and substantial verification are complete; final Java 17 verification, all-report demo smoke test, documentation and final polish remain.
