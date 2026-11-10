# Recorded walkthrough

The recording is a real Playwright browser session against the running local application. It includes Hinglish on-screen explanations and highlighted clicks, with a separate speaking script. There is no audio track. The caption panel exists only in the recording browser; it is not a product UI change.

The recording browser uses Playwright's `bypassCSP` option solely to render the injected caption styling. The application's nginx security policy is unchanged; this recording is not a CSP security test.

## Watch the result

The completed 5 November recording is in `.local/walkthrough/2026-11-05-37b46d/`: **208.4 seconds**, **1440 × 1000**, **23 chapters**. The portable ZIP is `.local/walkthrough/Reconflow-Hinglish-Walkthrough-2026-11-05.zip`. Actual financial/import/claim assertions passed, there were no browser page errors, and playback/chapter/play/pause/speed checks passed. Mapping, results and claim frames were visually inspected. [Recorded validation summary](validation/walkthrough-2026-11-05.json).

Open `WATCH.html` in the generated `.local/walkthrough/<date>-<id>/` folder with Chrome or Edge. Use the chapter buttons to jump between steps. The same folder contains:

- `reconflow-hinglish-walkthrough.webm`: standalone screen recording.
- `HINGLISH-SCRIPT.md`: timed narration to practise for an interview.
- `chapters.json`: run outcome, chapter times and page errors.
- `orders.csv` and `settlements.csv`: the synthetic reports actually uploaded.
- `claim-evidence.json`: evidence actually downloaded from the application.

The browser video is WebM, the format produced by Playwright. Keep the video beside `WATCH.html` when copying the folder.

## Demonstrated example

A unique synthetic partner is created with a channel rule: zero commission, INR 90 fixed fee, INR 96 return fee and a seven-calendar-day settlement schedule. The recording then uploads three valid INR 1,000 orders plus one deliberately invalid amount, followed by four unique settlement transactions plus one deliberate duplicate.

| Example | Expected | Received | Result |
| --- | ---: | ---: | --- |
| SHORT | 910 | 873 | INR 37 short-paid, claim created and moved to IN_REVIEW |
| SPLIT | 910 | 500 + 410 | SETTLED |
| TAX | 910 - TCS 10 - TDS 5 = 895 | 895 | SETTLED; taxes separate from leakage |

The run asserts accepted/rejected counts, all three order results, INR 37 dashboard risk, downloaded claim amount and absence of browser page errors. Earlier application regression and benchmark results are described in the captions as measured results, not rerun during recording.

AI suggestions are not invoked during recording. The current account's credit exhaustion is disclosed in the captions; manual mapping is demonstrated. Password fields remain masked, no `.env` view or API key is recorded, and traces/storage-state credentials are not exported. Synthetic records, rates and the internal claim remain in the local database. Nothing is submitted to an external partner.

## Reproduce

Start the existing app first. From the project root in PowerShell:

```powershell
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path (Get-Location).Path '.local/playwright'
$env:BASE_URL = 'http://127.0.0.1:15173'
cd frontend
npx.cmd playwright install ffmpeg
node scripts/record-walkthrough.mjs
```

Dependencies must already be installed with `npm ci`. The recorder discovers installed Chrome/Edge or accepts `PLAYWRIGHT_EXECUTABLE_PATH`. It uses local `.env` demo credentials, makes no provider requests, and creates a new uniquely named synthetic dataset each time. It does not overwrite previous recordings or reports. A failed attempt exits nonzero and keeps its failure screenshot and chapter metadata for diagnosis; only a run with `passed: true` should be used as the completed demonstration.
