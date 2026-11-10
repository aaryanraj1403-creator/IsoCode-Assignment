# Reconflow

Reconflow reconciles a D2C brand’s orders, settlements, COD remittances, freight invoices and warehouse returns. Finance teams can approve report mappings, inspect differences, prepare claims with source evidence and monitor money at risk.

**Stack:** React 18 + TypeScript, Java 17 / Spring Boot, PostgreSQL, Flyway and Docker Compose. The backend is a modular monolith.

## Run locally

Run commands from this folder, which contains `backend`, `frontend` and `compose.yaml`.

1. If `.env` does not already exist, copy `.env.example` to `.env`. Set a database password and two demo passwords of at least 12 characters.
2. With Docker Desktop running:

   ```powershell
   docker compose up --build --wait
   ```

3. Open **http://127.0.0.1:5173**. Choose Finance executive (`finance`) or Co-founder (`founder`) and use the corresponding password from `.env`.

For Windows development without Docker, existing installations and portable tooling are supported:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-database.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1
```

The database script uses a project-local cluster on port **55432**. Set `DATABASE_URL=jdbc:postgresql://127.0.0.1:55432/reconflow?reWriteBatchedInserts=true` in `.env` for this route. In another terminal:

```powershell
cd frontend
npm ci
npm run dev -- --host 127.0.0.1
```

See [deployment.md](deployment.md) for prerequisites, explicit tool paths, test database setup, environment variables, HTTPS/session settings and troubleshooting.

## Try the complete flow

Sign in → Import centre → choose partner and report type → upload CSV or XLSX → review mappings and the 20-row preview → approve processing → inspect reconciliation → create a claim → export evidence → review the dashboard.

- Both roles can import, reconcile, approve manual matches and manage claims.
- Only the co-founder can create rate-card versions or restore a mapping version.
- Overview, imports, reconciliation, claims and unmatched records have database-backed filtering/search; the report, reconciliation, claims and unmatched lists are paginated.
- An active settlement claim blocks a second claim even when an unpaid exception becomes short-paid after a partial payment.
- Required mappings and a preview by the approving user are enforced on the backend.
- Files, original row numbers, mapping versions and calculation details remain available as evidence.
- Claims are prepared locally. No external claim is submitted.

The three original client examples are preserved. Their expected amounts are labelled as authoritative brief values. Other orders use dated rate cards.

## Reports and demo data

Upload UTF-8 CSV or a single-sheet XLSX containing values only. Limits are 30 MB, 250,000 data rows and 100 columns per report. Dates use `YYYY-MM-DD`; amounts use plain decimals with no grouping separators and at most two decimal places.

Report types are `ORDERS`, `SETTLEMENTS`, `COD`, `FREIGHT` and `RETURNS`. The selected partner scopes every order and transaction ID. Return receipts are distinct from orders expecting a return.

Ready-to-use synthetic CSV/XLSX files are in [sample-data/synthetic](sample-data/synthetic). Generate more or load the verified scenarios through the API:

```powershell
python scripts/generate_reports.py --rows 40 --xlsx
python scripts/load-demo.py --xlsx
```

The loader intentionally includes rejected rows and duplicate transactions. Its 40 accepted orders cover settled payments, ₹10 tolerance, split receipts, legitimate tax deductions, refunds, late COD, freight overcharges, pending payments and missing returns. It creates synthetic data and retains it.

## AI mapping

Manual and saved mappings work without AI. To enable genuine OpenAI mapping suggestions, set `OPENAI_API_KEY` and `OPENAI_MODEL` on the backend and restart it. Choose a model available to your account that supports the Responses API and structured output.

Mapping requests send only column headers. Optional record-match suggestions send opaque candidate tokens and redacted comparison facts, with no order/transaction identifiers or raw monetary values. Suggestions are validated and require explicit human approval; match proposals are bound to the account, transaction and order and expire after 24 hours. Missing credentials, refusal, timeout or invalid output produces an explicit manual fallback. No API key is exposed to the frontend. Live provider access is tracked separately in the [validation report](docs/VALIDATION.md).

## Verification and design

- [Technical Design Document source](docs/TECHNICAL_DESIGN.md): architecture, schema, financial rules, evidence, AI behavior and tradeoffs.
- [Decisions and assumptions](docs/DECISIONS.md): rates, schedules, taxes, returns and scope.
- [Requirement coverage](docs/REQUIREMENTS.md): implemented, tested and externally dependent requirements.
- [Recorded walkthrough guide](docs/WALKTHROUGH.md): reproduce the real Playwright demo with Hinglish captions and interview notes.
- [Validation report](docs/VALIDATION.md): actual tests, benchmark timings, environment and verification limits.
- [Deployment guide](deployment.md): reproducible commands and operational setup.

Backend checks use `mvn package`; real PostgreSQL integration tests additionally require the dedicated `TEST_DATABASE_*` variables described in the deployment guide. Frontend checks:

```powershell
cd frontend
npm run build
npm run test:e2e
```

The HTTP benchmark processes equal-sized order and settlement files, including source-row storage and reconciliation. `--rows` is the number of rows **per file**:

```powershell
python scripts/benchmark.py --rows 50000  # 100,000 total rows across two reports
python scripts/benchmark.py --rows 100000 # 200,000-row stress run
```

It records actual timing and runtime versions. Dataset generation, human review time and cold JVM startup are excluded. A domain-only aggregation timing is not used as evidence of full processing speed.

This repository has no invented GitHub or live deployment URL. Nothing is pushed or published by the setup scripts.
