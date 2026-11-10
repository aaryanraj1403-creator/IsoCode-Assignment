# Reconflow - reviewer setup and deployment

Candidate: Aryan Raj  
GitHub: https://github.com/aaryanraj1403-creator/IsoCode-Assignment  
Public deployment: none verified. The URL below is local only.

The public repository now contains the reviewed source at its root on branch main. An anonymous shallow clone on 10 November 2026 succeeded and returned 99 source/docs files; `.env`, `.local`, dependencies and build output were absent. Git history begins with the submission commits, so earlier local development history is not represented. The ZIP deliberately includes only this guide and the technical design PDF; it is not a source archive.

## Prerequisites

- Docker Desktop running Linux containers and Docker Compose v2 or later.
- Windows PowerShell for the supplied isolated-reviewer setup script.
- Native alternative: Java 17 JDK, Maven 3.9+, Node.js 20+ with npm, PostgreSQL 16+.
- Python 3.10+ for the synthetic generator, HTTP loader and benchmark (standard library).
- Chrome/Edge or Playwright Chromium for browser tests.
- Internet for first-time images and dependencies. Use one application runtime at a time on a small-memory workstation.

## Obtain source and locate the root

After the candidate publishes the reviewed source:

```powershell
git clone https://github.com/aaryanraj1403-creator/IsoCode-Assignment.git
cd IsoCode-Assignment
```

The repository root itself contains `backend`, `frontend`, `compose.yaml`, `scripts` and the sample data, so the clone directory is the project root. Run all remaining root commands inside `IsoCode-Assignment`.

## Recommended: isolated synthetic reviewer application

From the project root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-reviewer.ps1
docker compose --env-file .local/reviewer.env -p reconflow-reviewer config --quiet
docker compose --env-file .local/reviewer.env -p reconflow-reviewer up -d --build --wait --wait-timeout 180
docker compose --env-file .local/reviewer.env -p reconflow-reviewer ps
```

Open **http://127.0.0.1:16173**. Backend health: **http://127.0.0.1:19080/api/health**. Do not mix `localhost` and `127.0.0.1` in a session.

The setup script creates `.local/reviewer.env` with a private randomly generated database password. It preserves an existing reviewer file and never overwrites root `.env`. The project uses independent database/upload volumes. PostgreSQL has no exposed host port. Backend/nginx bind only to loopback. AI credentials are blank by default.

Intentionally public logins, for this isolated synthetic reviewer setup only:

| Role | Username | Password |
| --- | --- | --- |
| Finance executive | finance | Review-Finance-2026! |
| Co-founder | founder | Review-Founder-2026! |

Startup provisions both accounts with BCrypt hashes. If an existing reviewer environment was customized, its configured values apply instead. Never use these public credentials for real data or a public deployment. They are application demo accounts, not database or infrastructure credentials.

Stop without deleting evidence:

```powershell
docker compose --env-file .local/reviewer.env -p reconflow-reviewer stop
```

Do not use `down -v` when preserving reports and claims. Ports can be changed when first creating the reviewer file using `-FrontendPort` and `-BackendPort` on the setup script; on an existing file edit those two entries privately.

## Environment variables

| Name | Purpose |
| --- | --- |
| DATABASE_PASSWORD | Required private database credential. Reviewer script generates it locally. |
| DATABASE_USER | Native PostgreSQL user; default reconflow. Compose uses reconflow. |
| DATABASE_URL | Native JDBC URL; Compose supplies its internal database URL with reWriteBatchedInserts=true. |
| DEMO_FINANCE_PASSWORD | Backend password for finance; at least 12 characters. |
| DEMO_FOUNDER_PASSWORD | Backend password for founder; at least 12 characters. |
| OPENAI_API_KEY | Optional backend-only OpenAI key; never put in frontend variables. |
| OPENAI_MODEL | Account-accessible Responses/structured-output model; needed with the key. |
| RETURN_GRACE_DAYS | Missing-return grace period; default 14, range 1..365. |
| SESSION_COOKIE_SECURE | false for local HTTP; true behind HTTPS. |
| UPLOAD_DIR | Persistent originals; Compose /app/uploads, native ./uploads relative to backend. |
| JAVA_TOOL_OPTIONS | Optional JVM bounds; Compose defaults to 384 MB heap and two active processors. |
| FRONTEND_PORT / BACKEND_PORT | Host ports; reviewer setup uses 16173 / 19080. |
| TEST_DATABASE_URL | Dedicated PostgreSQL integration-test JDBC URL. Never use the application database. |
| TEST_DATABASE_USER / TEST_DATABASE_PASSWORD | Dedicated test database credentials. |
| BASE_URL | Browser/HTTP test origin. |
| PLAYWRIGHT_EXECUTABLE_PATH | Optional local Chrome/Edge/Chromium executable override. |

Do not print environment files or add secrets to Git. Files use simple `NAME=value` entries. Compose reads the selected env file; Python and native scripts read root `.env`, with process variables used by the Python clients taking precedence.

## Migrations and synthetic seeds

Flyway runs V1-V4 automatically on backend startup. `spring.sql.init.mode=never` prevents legacy schema.sql from running alongside migrations. V1 preserves the brief's three example orders and creates illustrative channel/courier rate cards. No manual SQL seed command is required.

For CSV/XLSX scenarios covering duplicates, splits, invalid rows, COD, freight, taxes and returns:

```powershell
$env:DEMO_FINANCE_PASSWORD='Review-Finance-2026!'
python scripts/load-demo.py --base http://127.0.0.1:16173 --xlsx
```

This generates uniquely prefixed synthetic data and imports all five report types through preview/approval. It expects 40 orders, five deliberate row errors, six states and INR 5,992 risk for that prefix. Repeated runs retain earlier records. For manual UI uploads use `sample-data/synthetic`, partner Website. `sample-data/orders.csv` is the preserved brief reference and lacks the required dates for the current upload contract.

## Native development alternative

Use a PostgreSQL installation and privately configure root `.env` from `.env.example`; preserve an existing file. Set DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD and both application passwords. Example non-secret URL: `jdbc:postgresql://127.0.0.1:5432/reconflow?reWriteBatchedInserts=true`. Create the database using your own authorized local administrator and password prompt:

```powershell
createdb -h 127.0.0.1 -U postgres -W reconflow
```

Set DATABASE_USER to the database role you actually provisioned; this command alone does not create a reconflow role. With Java 17 and Maven on PATH, from the root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1
```

The script loads root `.env`, builds/packages the backend and runs a copied JAR. In a second terminal:

```powershell
cd frontend
npm ci
npm run dev -- --host 127.0.0.1
```

Open http://127.0.0.1:5173; Vite proxies /api to backend port 8080. Use Ctrl+C to stop foreground processes. Native accounts use your root environment values, not necessarily the public reviewer passwords.

## Build and tests

Frontend, from its directory:

```powershell
npm ci
npm run build
$env:BASE_URL='http://127.0.0.1:16173'
$env:DEMO_FINANCE_PASSWORD='Review-Finance-2026!'
$env:DEMO_FOUNDER_PASSWORD='Review-Founder-2026!'
npm run test:e2e
```

The browser configuration uses installed Chrome/Edge if present. Otherwise run `npx playwright install chromium` before tests. Tests create uniquely named synthetic reports/claims and preserve them. Run them against a freshly started reviewer stack; repeated interrupted runs can leave extra AWAITING MAPPING rows, so reset the isolated reviewer volumes before a clean assertion run.

Backend unit/parser/provider tests, from backend: `mvn test`. Without TEST_DATABASE_URL, 21 tests run and the 11 database integration tests are skipped; this is not a full integration pass.

The full suite was verified on 10 November 2026 with Java 17.0.21 and Maven 3.9.16 against a disposable PostgreSQL 16 container:

```powershell
$testDbPassword=[guid]::NewGuid().ToString('N')
docker run -d --rm --name reconflow-testdb -e POSTGRES_DB=reconflow_test -e POSTGRES_USER=reconflow_test -e POSTGRES_PASSWORD=$testDbPassword -p 127.0.0.1:55433:5432 postgres:16-alpine
for($i=0;$i -lt 30;$i++){if((docker exec reconflow-testdb pg_isready -U reconflow_test -d reconflow_test 2>$null) -match 'accepting'){break};Start-Sleep 1}
# Use JDK 17 on PATH, or set JAVA_HOME to your JDK 17 installation.
$env:TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:55433/reconflow_test'
$env:TEST_DATABASE_USER='reconflow_test'
$env:TEST_DATABASE_PASSWORD=$testDbPassword
cd backend
mvn -B test
```

Expected result: 32 tests, 0 failures, 0 errors, 0 skips, including 11 real PostgreSQL integration tests. Remove only the disposable container when finished: `docker rm -f reconflow-testdb`.

Alternative fully Docker route (not rerun in the dated check), if Java/Maven are not installed: create `reconflow_test` inside the reviewer database, then run the backend source through Maven in a container on the reviewer network:

```powershell
docker compose --env-file .local/reviewer.env -p reconflow-reviewer exec -T database createdb -U reconflow reconflow_test
# If it already exists, preserve it and continue; use only this dedicated test database.
foreach ($line in Get-Content .local/reviewer.env) {
  if ($line -match '^DATABASE_PASSWORD=(.*)$') { $env:TEST_DATABASE_PASSWORD=$matches[1] }
}
$env:TEST_DATABASE_URL='jdbc:postgresql://database:5432/reconflow_test'
$env:MAVEN_OPTS='-Xms32m -Xmx256m -XX:ActiveProcessorCount=2'
$backendPath=Join-Path $PWD 'backend'
docker run --rm --memory 1g --cpus 2 --network reconflow-reviewer_default --mount "type=bind,source=$backendPath,target=/workspace" -w /workspace -e TEST_DATABASE_URL -e TEST_DATABASE_PASSWORD -e MAVEN_OPTS maven:3.9-eclipse-temurin-17 mvn -B package
```

Integration tests exercise authentication/CSRF and role restrictions, preview binding, uploads/duplicates/splits, financial boundaries/taxes/refunds, rate dates, claims/evidence/dashboard and approved matching against real PostgreSQL. AI wire tests use a local fake HTTP provider to verify transport/validation/failure behavior, not a live account.

## Walkthrough

1. Sign in as finance; inspect overview totals, partner tax summaries and aging.
2. Import a synthetic order CSV/XLSX with partner Website and type ORDERS. Review headers and map required fields.
3. Validate the 20-row preview, tick approval and process. Inspect accepted/error counts and row errors.
4. Import settlement/COD and other reports with their matching types. Distinct transaction IDs permit split payments; repeated IDs/files are rejected.
5. Open reconciliation, filter/search an order and inspect expected/received, applicable rate version, source rows and active exceptions.
6. Create a claim, move it to IN_REVIEW with a note, and export evidence JSON. Duplicate active claims are blocked. Nothing is sent to partners.
7. Sign out and sign in as founder. Inspect rate cards and add a non-overlapping version for a synthetic partner. Finance cannot write rate versions.
8. With AI unset, Suggest with AI displays an unavailable message; manual mapping still works. Do not describe this fallback as an AI-generated suggestion.

## Performance

```powershell
$env:DEMO_FINANCE_PASSWORD='Review-Finance-2026!'
python scripts/benchmark.py --base http://127.0.0.1:16173 --rows 50000 --out .local/benchmark-reviewer.json
```

Rows means per file: 50,000 orders + 50,000 settlements = 100,000 total. The HTTP benchmark includes parsing, database writes, preview/approval and reconciliation. It excludes generation, human delay and cold startup. Run it separately from builds and browser tests. A fresh Docker run on 10 November 2026 took 22.443 seconds for 100,000 total rows (13.497 + 8.946 per file), accepting all rows. The retained 4 November run took 25.952 seconds for the same shape; the 200,000-total-row stress run took 80.079 seconds. No new timing is implied by the historical measurements.

## Actual validation and limitations

Verification rerun on 10 November 2026 (IST): backend Maven test passed 32 tests, 0 failures/errors/skips, including 11 real PostgreSQL 16.15 integration tests, in 24.588 seconds with Java 17.0.21. A run without TEST_DATABASE_URL skipped exactly those 11 tests. Frontend TypeScript/Vite production build passed (38 modules; JavaScript 184.82 kB, gzip 57.44 kB; CSS 7.02 kB). Docker Compose configuration validation, image rebuild and isolated stack startup passed; PostgreSQL, backend and frontend all healthy and /api/health UP. All three production browser workflows passed on a clean reviewer stack, including both roles, session cookie checks, upload/approval, claims/evidence download, mobile rate editing and manual matching. XLSX HTTP scenario verification passed across five report types: 40 orders, five deliberate rejected rows, six statuses and INR 5,992 risk. A fresh 100,000-total-row Docker benchmark passed in 22.443 seconds.

The current source uses the production OpenAI Responses endpoint. On 10 November a direct model-metadata request succeeded, but a real Responses request and the in-app Suggest with AI call returned HTTP 429 credit_balance_exhausted. Successful live AI output remains unverified and requires available account credits/model access. No key is included here. The reviewer instance intentionally exercises manual fallback; the unavailable message is the expected current behavior without credits.

There is no verified public application deployment. Production TLS, managed identity, multi-instance sessions/jobs, backups, retention/redaction, working-day schedules, item-level returns and courier slabs are not part of this take-home. Large XLSX and concurrent throughput remain unverified. The public source and an anonymous clone were verified; Git history begins with the submission commits, so earlier local development history is not represented.
