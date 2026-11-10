# Setup and deployment

The verified hand-back environment is currently the Docker project `reconflow-validation` at **http://127.0.0.1:15173**, with backend port 18080. Its database/upload volumes contain the functional demo and browser-test evidence. The native benchmark database is preserved but stopped. Use one runtime at a time on this workstation; the measured Windows memory/page-file budget could not reliably support the native large-data restart alongside Docker Desktop.

## Prerequisites

- Docker Desktop with the Linux engine and Docker Compose for the container route.
- For native development: Java **17 JDK**, Maven 3.9+, Node.js 20+ / npm and PostgreSQL 16+.
- Python 3.10+ for the standard-library dataset generator, demo loader and benchmark.
- Chrome/Edge installed, or a Playwright Chromium installation, for browser tests.

The repository contains no production credentials. Keep the root `.env` private; it is ignored. If it already exists, preserve its values. Otherwise copy `.env.example` and set the three passwords. Demo passwords must contain at least 12 characters.

## Environment variables

| Variable | Purpose |
| --- | --- |
| DATABASE_PASSWORD | PostgreSQL password; required. |
| DATABASE_URL | Native JDBC connection URL. Default: localhost:5432/reconflow. Compose supplies its internal database URL. Use `reWriteBatchedInserts=true` for efficient batch inserts. |
| DATABASE_USER | Native database user; defaults to reconflow. |
| DEMO_FINANCE_PASSWORD | Environment-supplied password for username finance. Hashed with BCrypt in the database. |
| DEMO_FOUNDER_PASSWORD | Environment-supplied password for username founder. Hashed with BCrypt in the database. |
| OPENAI_API_KEY | Optional backend-only provider key. |
| OPENAI_MODEL | Optional Responses/structured-output model available to your account. Both key and model are needed for AI suggestions. |
| RETURN_GRACE_DAYS | Default 14; integer 1–365. Expected warehouse return deadline is order date plus this value unless an explicit return_due is supplied. |
| SESSION_COOKIE_SECURE | false for local HTTP; true when serving the application through HTTPS. |
| JAVA_TOOL_OPTIONS | Optional JVM override. Native startup defaults to a 512 MB heap; Compose defaults to a 384 MB heap. |
| UPLOAD_DIR | Source-file storage directory. Native default: backend/uploads. Compose uses /app/uploads on a persistent volume. |
| FRONTEND_PORT | Compose host port; defaults to 5173. |
| BACKEND_PORT | Compose host port; defaults to 8080. |
| TEST_DATABASE_URL | Dedicated PostgreSQL database for integration tests; use a database such as reconflow_test. |
| TEST_DATABASE_USER | Integration-test user; defaults to reconflow. |
| TEST_DATABASE_PASSWORD | Password for the dedicated integration-test database. |
| BASE_URL | Optional Playwright base URL; defaults to http://127.0.0.1:5173. |
| PLAYWRIGHT_EXECUTABLE_PATH | Optional browser executable override for Playwright. |

The Windows scripts and Python clients read simple `NAME=value` lines from root `.env`. Use bare values without shell quotes or interpolation. They do not echo passwords. Compose reads the same file.

## Docker Compose

With the Linux engine running:

```powershell
docker compose config --quiet
docker compose up --build --wait
docker compose ps
```

Open http://127.0.0.1:5173. The frontend nginx server proxies API requests to the backend; the database is not exposed on a host port. The backend is also available at http://127.0.0.1:8080/api/health.

The database and uploads use separate persistent volumes. Migrations run automatically before authentication is initialized. A non-root Java 17 runtime serves the backend. The frontend image builds the TypeScript production bundle.

Compose limits the backend to 768 MB, PostgreSQL to 512 MB and nginx to 128 MB. The backend heap is 384 MB. Native startup uses a 512 MB heap, and test JVMs use 384 MB; Maven startup uses a 256 MB heap. These bounds keep verification practical on the measured machine with approximately 7.3 GiB usable RAM. Run large benchmarks separately from builds/browser tests; other running applications still affect available memory and timing.

A reproducible Windows startup check is supplied:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/check-compose.ps1
```

If native development already uses 5173/8080, validate an independent Compose project on alternate ports:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/check-compose.ps1 -ProjectName reconflow-validation -FrontendPort 15173 -BackendPort 18080
```

The separate project has its own database and upload volumes. Stopping a project with `docker compose --project-name NAME stop` retains data. Do not remove volumes when you want to preserve reports and claims.

## Native Windows development

Configure an existing PostgreSQL database with `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD`, or use portable PostgreSQL:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-database.ps1 -PostgresBin "C:\tools\pgsql\bin"
```

This script initializes only the project’s `.local/postgres` cluster if missing, authenticates with SCRAM and listens on 127.0.0.1:55432. It never modifies the installed PostgreSQL service. For this route, root `.env` should contain:

```text
DATABASE_URL=jdbc:postgresql://127.0.0.1:55432/reconflow?reWriteBatchedInserts=true
DATABASE_USER=reconflow
```

Run the backend:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -MavenPath "C:\tools\apache-maven\bin\mvn.cmd" -JavaHome "C:\tools\jdk-17"
```

The script also discovers portable Maven/PostgreSQL archives extracted in Downloads and a JDK under `.local/jdk17`. Explicit arguments are more portable. It loads `.env`, builds/tests the backend and copies the executable JAR to `.local/runtime` before running it. This permits later builds without Windows locking `target`. `-NoBuild` runs an existing packaged JAR.

In a separate terminal:

```powershell
cd frontend
npm ci
npm run dev -- --host 127.0.0.1
```

Use the Vite origin for the entire application. Session cookies are same-origin; do not mix localhost and 127.0.0.1 during one login flow. Ctrl+C stops the foreground Java or Vite process. To stop the portable database cleanly:

```powershell
& "C:\tools\pgsql\bin\pg_ctl.exe" -D ".local/postgres" -m fast -w stop
```

## Demo login and operation

Choose Finance executive (`finance`) or Co-founder (`founder`) and enter the corresponding environment password. The backend provisions these two users after migrations, stores BCrypt hashes and refreshes the hashes from the configured environment at startup. Passwords are not stored in frontend code.

Both roles can upload, preview/approve mappings, reconcile, approve manual matches and manage claims. Only the co-founder can add rate-card versions and restore mapping versions. Sign-out invalidates the session and removes its cookie. Sessions expire after 30 minutes of inactivity.

Use the files in `sample-data/synthetic` with partner Website. The older `sample-data/orders.csv` preserves the original brief; it lacks the dates required by the current import format. It is reference evidence rather than a ready-to-import order report.

For an API-driven scenario check:

```powershell
python scripts/load-demo.py --xlsx
```

The loader creates uniquely prefixed synthetic records. It expects 40 valid orders, five intentional rejected rows across the order/settlement files, and ₹5,992 total risk for that prefix. It does not delete prior runs.

## Backend verification

Run unit/parser/AI validation tests and package:

```powershell
cd backend
mvn package
```

Integration tests are skipped unless `TEST_DATABASE_URL` is present. Create a separate database first using your PostgreSQL tooling:

```powershell
createdb -h 127.0.0.1 -p 55432 -U reconflow reconflow_test
```

From the project root, load local configuration into this PowerShell process and run the full suite:

```powershell
. ./scripts/env.ps1
Import-ReconflowEnvironment (Get-Location).Path
$env:TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:55432/reconflow_test'
$env:TEST_DATABASE_PASSWORD=$env:DATABASE_PASSWORD
cd backend
mvn package
```

Use Java 17 via process-local `JAVA_HOME`/PATH or your IDE JDK. The test database receives synthetic records and demo test users; never point it at a real/reviewer database. Test cases use distinct partner IDs and retain evidence, so repeated runs do not delete another test run’s records. The integration suite overrides AI credentials to keep tests offline.

## Browser and performance verification

With the app running:

```powershell
cd frontend
npm run build
npm run test:e2e
```

Playwright discovers installed Windows Chrome/Edge. On other systems install a supported browser with `npx playwright install chromium`, or set `PLAYWRIGHT_EXECUTABLE_PATH`. The tests read local demo passwords from `.env` or the environment and add synthetic reports, claims and rate cards. Traces/reports are ignored; review their contents before sharing because browser traces can contain session data.

The reproducible benchmark runs through HTTP against the same database-backed flow:

```powershell
python scripts/benchmark.py --rows 50000 --out .local/benchmark-100k-total.json
python scripts/benchmark.py --rows 100000 --out .local/benchmark-200k-total.json
```

`--rows` specifies rows per file: 50,000 produces 100,000 total input rows; 100,000 produces a 200,000-row stress run. Timing starts before upload and ends after both imports finish reconciliation. Parsing, source storage, 20-row previews, approval requests, validation, database writes and HTTP polling are included. Generation, human review delay and cold JVM startup are excluded. All generated benchmark orders must be SETTLED. The JSON report includes actual Java/PostgreSQL versions and the count of pre-existing orders.

Run performance checks without concurrent browser tests or imports. Each run retains another synthetic dataset. Results depend on hardware, database growth, filesystem and concurrent services; the latest measured limits are in `docs/VALIDATION.md`.

## AI, HTTPS and persistence

AI remains optional. Configure both backend variables, restart the backend, and click Suggest with AI during mapping review or Suggest match with AI in unmatched records. Mapping uses headers only; record matching uses opaque/redacted comparison facts. Proposals remain unapplied until explicit account-bound approval, and expire after 24 hours. Real provider success requires network access, a valid key, account/model access and provider capacity. Provider errors never silently fall back to fabricated AI output.

After changing `.env`, recreate the backend with `docker compose up -d backend`; a plain container restart does not load changed Compose environment values. For the existing validation project, keep `FRONTEND_PORT=15173` and `BACKEND_PORT=18080` in the current shell and use `docker compose -p reconflow-validation up -d backend`.

An opt-in live check uses synthetic data and normally makes two provider requests, which can incur API charges:

```powershell
python scripts/verify-live-ai.py --base http://127.0.0.1:15173 --out .local/live-ai-verification.json
```

It checks real mapping output, preview/approval, a missing-ID receipt, a provider match proposal and explicit match approval before settlement. It retains its synthetic data and saves a result without credentials. It stops on failure and does not retry billing errors. The 5 November run was blocked by `credit_balance_exhausted`; restore API credits in the account's billing settings before repeating it. The existing three browser regression workflows were validated with AI unconfigured and assert that fallback; this live script is a separate opt-in provider check.

For HTTPS deployment, use a trusted TLS reverse proxy and `SESSION_COOKIE_SECURE=true`. Keep UI and API on the same origin. Do not set Secure cookies for local HTTP. CSRF remains enabled for login, logout and all mutations. The production nginx configuration supplies a same-origin content policy and attachment downloads use explicit filenames.

Back up PostgreSQL and the source-file volume together; database rows reference stored files. In-memory sessions are lost at restart. Current-day reconciliation snapshots are retained at startup; missing or stale results trigger refresh. Parsing/processing imports interrupted by restart are marked FAILED and can be retried from retained sources. Run one backend instance with the current job executor/recovery model.

There is no automatic hosting, Git push or external claim submission. See the technical design for remaining production work and the validation report for checks actually executed.
