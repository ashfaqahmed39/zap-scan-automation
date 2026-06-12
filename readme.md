# Security Testing Automation

Java/Maven security test automation project for running OWASP ZAP scans against a web application. The project uses TestNG for test execution, Selenium ChromeDriver for browser traffic, and the ZAP Java Client API for spidering, passive scanning, active scanning, authenticated scanning, API-definition scanning, alert threshold checks, and report generation.

The main workflow is designed around one combined test class:

```text
src/test/java/CombinedSecurityScanTest.java
```

That class contains two primary scan methods:

```text
testCombinedUnauthenticatedSecurityScan
testCombinedAuthenticatedSecurityScan
```

The default Maven run executes only the unauthenticated combined scan. The authenticated combined scan is available through a separate TestNG suite XML.

## What This Project Does

- Sends Selenium browser traffic through the ZAP proxy.
- Creates ZAP contexts scoped to `APP_BASE_URL`.
- Runs traditional spider and Ajax spider.
- Runs forced-browse checks for common hidden paths.
- Runs passive scan waits before and after active scanning.
- Runs active scanning with configurable ZAP policies.
- Supports unauthenticated and authenticated scan flows.
- Supports authenticated workflow URL visits after login.
- Generates one timestamped ZAP HTML report per test run.
- Provides optional API-definition scanning through OpenAPI/Swagger.
- Provides optional alert threshold gates.
- Provides optional baseline false-positive filtering and delta reports.

## Important Safety Notes

Only run active scans against systems you own or have explicit permission to test.

Active scanning sends attack payloads and can create load, trigger alerts, modify data, or break fragile flows. Use a staging or test environment whenever possible.

For authenticated scans, avoid workflow URLs that perform destructive actions such as delete, payment, refund, password reset, sending real email/SMS, or changing production data.

## Prerequisites

- Java JDK 17
- Maven
- Google Chrome
- OWASP ZAP running locally or remotely
- ZAP API enabled
- Network access from this machine to the target application

The project uses these main dependencies from `pom.xml`:

- `org.testng:testng`
- `org.zaproxy:zap-clientapi`
- `org.seleniumhq.selenium:selenium-java`
- `io.github.bonigarcia:webdrivermanager`
- `io.github.cdimascio:dotenv-java`

## Project Structure

```text
.
|-- pom.xml
|-- readme.md
|-- src
|   |-- main
|   |   `-- java
|   |       `-- utils
|   |           |-- ZapAlertUtil.java
|   |           |-- ZapApiUtil.java
|   |           |-- ZapAuthUtil.java
|   |           |-- ZapPolicyUtil.java
|   |           `-- ZapUtil.java
|   `-- test
|       |-- java
|       |   |-- ActiveScanTest.java
|       |   |-- AlertThresholdGateTest.java
|       |   |-- ApiDefinitionScanTest.java
|       |   |-- AuthSpiderActiveScanTest.java
|       |   |-- BaselineFalsePositiveTest.java
|       |   |-- CombinedSecurityScanTest.java
|       |   |-- PassiveScanTest.java
|       |   |-- PolicyBasedActiveScanTest.java
|       |   `-- SpiderAPScanTest.java
|       |-- resources
|       |   |-- scan-thresholds.json
|       |   `-- zap-baseline-ignore-rules.json
|       |-- testng.xml
|       `-- testng-authenticated.xml
|-- reports
`-- target
```

## Main Files

`CombinedSecurityScanTest.java`

Contains the primary scan flows. Use this class for normal unauthenticated and authenticated combined scans.

`ZapUtil.java`

Shared ZAP helpers for proxy setup, passive scan waiting, spidering, Ajax spidering, forced browse, report generation, context setup, URL retrieval, and basic active scan helpers.

`ZapAuthUtil.java`

Authentication helpers for creating ZAP auth contexts, configuring form-based authentication, creating/enabling ZAP users, logging in with Selenium, spidering as a user, Ajax spidering as a user, and active scanning as a user.

`ZapPolicyUtil.java`

Creates and configures ZAP scan policies. Supports `policy-fast` and `policy-full`.

`ZapApiUtil.java`

Imports OpenAPI or GraphQL definitions and can trigger API-focused spider/active scan steps.

`ZapAlertUtil.java`

Fetches alerts, counts by risk, applies baseline ignore rules, writes delta reports, and asserts alert thresholds.

## ZAP Setup

Start ZAP before running tests.

Example local daemon start:

```bash
zap.sh -daemon -host 127.0.0.1 -port 8080
```

If your ZAP installation requires an API key, set `ZAP_API_KEY` in `.env` or your shell environment.

If using ZAP Desktop, make sure the API is enabled and reachable at the configured address/port.

Default project connection values:

```text
ZAP_ADDRESS=127.0.0.1
ZAP_PORT=8080
ZAP_API_KEY=
```

## Environment Configuration

The project reads values from operating system environment variables first, then from `.env`, then falls back to defaults.

Create a `.env` file in the project root. This file is ignored by Git.

Basic `.env` example:

```env
ZAP_ADDRESS=127.0.0.1
ZAP_PORT=8080
ZAP_API_KEY=
APP_BASE_URL=https://your-app.example.com
```

Authenticated `.env` example:

```env
ZAP_ADDRESS=127.0.0.1
ZAP_PORT=8080
ZAP_API_KEY=
APP_BASE_URL=https://your-app.example.com

AUTH_LOGIN_URL=https://your-app.example.com/login
AUTH_USERNAME=your-test-user@example.com
AUTH_PASSWORD=your-test-password
AUTH_USERNAME_CSS=#email
AUTH_PASSWORD_CSS=input[name='password']
AUTH_SUBMIT_CSS=button[type='submit']
AUTH_POST_LOGIN_CSS=.dashboard
AUTH_WORKFLOW_URLS=/outbound,/inbound,/dashboard,/settings
```

Do not commit real credentials.

## Environment Variables Reference

Core variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `APP_BASE_URL` | `http://127.0.0.1:8080` | Target application base URL. |
| `ZAP_ADDRESS` | `127.0.0.1` | ZAP API/proxy host. |
| `ZAP_PORT` | `8080` | ZAP API/proxy port. |
| `ZAP_API_KEY` | empty | ZAP API key if enabled. |

Scan policy variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `SCAN_SUITE` | `pr` | Use `full` or `nightly` for `policy-full`; otherwise `policy-fast`. |
| `TEST_SUITE` | `pr` | Fallback used by policy resolution if `SCAN_SUITE` is not set. |
| `ZAP_SCAN_POLICY` | empty | Explicit policy override, for example `policy-fast` or `policy-full`. |
| TestNG parameter `scanPolicyName` | `${scanPolicyName}` | Optional suite parameter passed from XML/Maven. |

Authentication variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `AUTH_LOGIN_URL` | `${APP_BASE_URL}/login` | Login page URL. |
| `AUTH_USERNAME` | empty | Username for authenticated scan. Required for authenticated suite. |
| `AUTH_PASSWORD` | empty | Password for authenticated scan. Required for authenticated suite. |
| `AUTH_USERNAME_CSS` | `#email` | CSS selector for username/email input. |
| `AUTH_PASSWORD_CSS` | `input[name='password']` | CSS selector for password input. |
| `AUTH_SUBMIT_CSS` | `button[type='submit']` | CSS selector for login submit button. |
| `AUTH_POST_LOGIN_CSS` | empty | Optional CSS selector to wait for after login. |
| `AUTH_LOGGED_IN_REGEX` | `(?i)(logout|sign out|my account)` | ZAP logged-in indicator regex. |
| `AUTH_LOGGED_OUT_REGEX` | `(?i)(login|sign in)` | ZAP logged-out indicator regex. |
| `AUTH_LOGIN_REQUEST_DATA` | `username={%username%}&password={%password%}` | ZAP form auth request template. |
| `AUTH_CREDENTIALS` | `username=<AUTH_USERNAME>&password=<AUTH_PASSWORD>` | ZAP user credential config. |
| `AUTH_WORKFLOW_URLS` | empty | Comma-separated URLs visited after Selenium login. |
| `AUTH_ONLY_URL_CONTAINS` | `/admin` | Used by the legacy authenticated test assertion. |

Forced browse variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `ZAP_FORCED_BROWSE_PATHS` | built-in list | Comma-separated paths to request during forced browse. |

API scan variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `OPENAPI_DEFINITION` | `${APP_BASE_URL}/v3/api-docs` | OpenAPI/Swagger URL or local file path. |
| `EXPECTED_IMPORTED_ENDPOINT_FRAGMENT` | `/api/` | Expected imported endpoint fragment for API test assertion. |

Alert gate variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `SCAN_THRESHOLDS_FILE` | `scan-thresholds.json` | Threshold JSON file for baseline gate. |
| `ZAP_BASELINE_IGNORE_FILE` | `zap-baseline-ignore-rules.json` | Ignore rules file for known/baseline alerts. |
| `ZAP_DELTA_REPORT_PATH` | `reports/zap-alert-delta-report.html` | Delta report output path. |

## Scan Policies

The project configures two policy profiles in `ZapPolicyUtil.java`.

`policy-fast`

Designed for faster PR/local checks. It disables all scanners first, then enables selected high-value active rules:

```text
40012 Reflected XSS
40018 SQL Injection
40003 CRLF Injection
90019 Server Side Code Injection
90020 Remote OS Command Injection
```

It uses lower attack strength and scan duration limits.

`policy-full`

Designed for deeper nightly/full scans. It enables all active scan rules and removes rule/scan duration limits.

Run full policy with:

```bash
SCAN_SUITE=full mvn test
```

Or explicitly:

```bash
ZAP_SCAN_POLICY=policy-full mvn test
```

## Running The Main Unauthenticated Combined Scan

Default command:

```bash
mvn test
```

This uses:

```text
src/test/testng.xml
```

It runs only:

```text
CombinedSecurityScanTest.testCombinedUnauthenticatedSecurityScan
```

Unauthenticated scan flow:

```text
Open APP_BASE_URL in Chrome through ZAP
Create scoped ZAP context
Run traditional spider
Run Ajax spider
Run forced browse
Wait for passive scan
Run policy active scan
Wait for passive scan again
Generate one HTML report
```

Use full scan policy:

```bash
SCAN_SUITE=full mvn test
```

## Running The Main Authenticated Combined Scan

Command:

```bash
mvn test -DsuiteXmlFile=./src/test/testng-authenticated.xml
```

This uses:

```text
src/test/testng-authenticated.xml
```

It runs only:

```text
CombinedSecurityScanTest.testCombinedAuthenticatedSecurityScan
```

Authenticated scan flow:

```text
Create authenticated ZAP context
Exclude logout/signout URLs from context
Configure ZAP form-based authentication
Create and enable ZAP user
Login with Selenium through ZAP
Visit AUTH_WORKFLOW_URLS while logged in
Run traditional spider as authenticated ZAP user
Run Ajax spider as authenticated ZAP user
Run forced browse through logged-in Selenium browser
Wait for passive scan
Run active scan as authenticated ZAP user
Wait for passive scan again
Generate one HTML report
```

Use full scan policy:

```bash
SCAN_SUITE=full mvn test -DsuiteXmlFile=./src/test/testng-authenticated.xml
```

If `AUTH_USERNAME` or `AUTH_PASSWORD` is missing, the authenticated scan is skipped.

## Authenticated Workflow URLs

`AUTH_WORKFLOW_URLS` is used to visit important logged-in pages that spiders may miss.

Example:

```env
AUTH_WORKFLOW_URLS=/outbound,/inbound,/dashboard,/settings
```

The test logs in first, then Selenium visits each URL through the ZAP proxy. This helps ZAP discover authenticated pages before spider and active scan run.

Relative and absolute URLs are supported:

```env
AUTH_WORKFLOW_URLS=/outbound,/inbound,https://your-app.example.com/settings
```

Prefer adding important view/search/list/detail pages. Avoid destructive action URLs.

## Forced Browse

Forced browse requests common hidden paths so ZAP can inspect responses and include them in the site tree/report.

Default paths include:

```text
/admin
/administrator
/backup
/backups
/config
/debug
/dev
/login
/logs
/old
/private
/server-status
/staging
/test
/uploads
/.env
/.git/HEAD
/robots.txt
/sitemap.xml
```

Override with:

```env
ZAP_FORCED_BROWSE_PATHS=/admin,/dashboard,/backup,/config,/api/docs
```

In the unauthenticated scan, forced browse uses ZAP `core.accessUrl()`. In the authenticated scan, forced browse uses Selenium after login so the browser sends authenticated cookies through ZAP.

## Reports

Each scan test generates a timestamped HTML report in:

```text
reports/
```

Filename format:

```text
dd-MM-yyyy_HH-mm-ss_ZAP-Report-<safe-host>.html
```

Example:

```text
reports/05-06-2026_16-45-52_ZAP-Report-dev.kothon.ai_.html
```

The `reports/` directory is ignored by Git.

## Optional API Definition Scan

The API definition scan is implemented in:

```text
src/test/java/ApiDefinitionScanTest.java
```

It does this:

```text
Create ZAP scope context
Run traditional spider
Record spider-discovered URLs
Import OpenAPI definition
Assert that imported-only API URLs exist
Run active scan against imported endpoints
Wait for passive scan
Generate report
```

Relevant variables:

```env
OPENAPI_DEFINITION=https://your-app.example.com/v3/api-docs
EXPECTED_IMPORTED_ENDPOINT_FRAGMENT=/api/
```

To run it directly with Maven Surefire method selection:

```bash
mvn test -Dtest=ApiDefinitionScanTest
```

API scan is intentionally not included in the default combined unauthenticated suite.

## Optional Alert Threshold Gate

The alert threshold gate is implemented in:

```text
src/test/java/AlertThresholdGateTest.java
```

It fetches alerts from ZAP for `APP_BASE_URL`, counts them by risk, and fails if counts exceed `scan-thresholds.json`.

Default thresholds:

```json
{
  "High": 0,
  "Medium": 0,
  "Low": 10,
  "Informational": 999
}
```

Run it after a scan while the ZAP session still contains alerts:

```bash
mvn test -Dtest=AlertThresholdGateTest
```

## Optional Baseline False-Positive Gate

The baseline false-positive gate is implemented in:

```text
src/test/java/BaselineFalsePositiveTest.java
```

It does this:

```text
Fetch all ZAP alerts for APP_BASE_URL
Load baseline ignore rules
Filter ignored alerts
Write a delta report
Fail only if actionable alert counts exceed thresholds
```

Default ignore file:

```text
src/test/resources/zap-baseline-ignore-rules.json
```

Default delta report:

```text
reports/zap-alert-delta-report.html
```

Run it after a scan:

```bash
mvn test -Dtest=BaselineFalsePositiveTest
```

## Legacy Single-Purpose Tests

These tests remain available for focused scan experiments or debugging:

| Test class | Purpose |
| --- | --- |
| `PassiveScanTest` | Opens the base URL and waits for passive scan. |
| `ActiveScanTest` | Opens the base URL, waits for passive scan, then runs active scan. |
| `SpiderAPScanTest` | Runs traditional spider, Ajax spider, passive wait, then in-scope active scan. |
| `PolicyBasedActiveScanTest` | Runs active scan with `policy-fast` or `policy-full`. |
| `AuthSpiderActiveScanTest` | Older authenticated spider/Ajax/active scan flow with assertions. |
| `ApiDefinitionScanTest` | Imports OpenAPI definition and scans imported endpoints. |
| `AlertThresholdGateTest` | Fails if ZAP alert counts exceed configured thresholds. |
| `BaselineFalsePositiveTest` | Filters baseline ignored alerts and fails only on actionable threshold breaches. |

Run a single legacy test with:

```bash
mvn test -Dtest=PolicyBasedActiveScanTest
```

## Recommended Local Workflow

1. Start ZAP.
2. Configure `.env` with `APP_BASE_URL` and ZAP connection settings.
3. Run quick unauthenticated scan.
4. Review the generated report in `reports/`.
5. Add authentication variables and `AUTH_WORKFLOW_URLS`.
6. Run authenticated scan.
7. Review the authenticated report.
8. Optionally run `BaselineFalsePositiveTest` or `AlertThresholdGateTest` after the scan.

Commands:

```bash
mvn test
mvn test -DsuiteXmlFile=./src/test/testng-authenticated.xml
mvn test -Dtest=BaselineFalsePositiveTest
```

## Recommended CI Workflow

Use `policy-fast` for pull requests or frequent runs:

```bash
SCAN_SUITE=pr mvn test
```

Use `policy-full` for nightly or scheduled scans:

```bash
SCAN_SUITE=full mvn test
```

Authenticated nightly example:

```bash
SCAN_SUITE=full mvn test -DsuiteXmlFile=./src/test/testng-authenticated.xml
```

Archive these outputs from CI:

```text
reports/*.html
target/surefire-reports/
```

## Common Commands

Compile without running scans:

```bash
mvn test-compile
```

Run default unauthenticated scan:

```bash
mvn test
```

Run authenticated scan:

```bash
mvn test -DsuiteXmlFile=./src/test/testng-authenticated.xml
```

Run full unauthenticated scan:

```bash
SCAN_SUITE=full mvn test
```

Run full authenticated scan:

```bash
SCAN_SUITE=full mvn test -DsuiteXmlFile=./src/test/testng-authenticated.xml
```

Run API definition scan only:

```bash
mvn test -Dtest=ApiDefinitionScanTest
```

Run threshold gate only:

```bash
mvn test -Dtest=AlertThresholdGateTest
```

Run baseline false-positive gate only:

```bash
mvn test -Dtest=BaselineFalsePositiveTest
```

## Troubleshooting

ZAP connection fails:

Check `ZAP_ADDRESS`, `ZAP_PORT`, and whether ZAP is running. Open the ZAP API URL in a browser or curl it from the same machine.

API key errors:

Set `ZAP_API_KEY` in `.env`, or disable API key enforcement in ZAP for local testing.

Chrome fails to start:

Make sure Chrome is installed. WebDriverManager downloads/manages ChromeDriver automatically, but it needs network access the first time.

CDP version warning:

Selenium may print a warning like `Unable to find an exact match for CDP version`. This is usually non-fatal if the scan continues. Update Selenium when needed.

Authenticated scan skips:

Set both `AUTH_USERNAME` and `AUTH_PASSWORD`.

Login fails:

Verify `AUTH_LOGIN_URL`, `AUTH_USERNAME_CSS`, `AUTH_PASSWORD_CSS`, `AUTH_SUBMIT_CSS`, and optionally set `AUTH_POST_LOGIN_CSS` to an element that appears only after successful login.

ZAP authenticated spider fails with illegal parameter:

Make sure the project is compiled with the current `ZapAuthUtil.java`. The current implementation uses numeric `contextId` and `userId` for traditional spider-as-user.

No authenticated URLs in report:

Add important pages to `AUTH_WORKFLOW_URLS` and confirm Selenium can reach them after login.

Report generation fails:

Make sure ZAP has the Reports add-on available. The project uses the `traditional-html` template and writes reports to `reports/`.

Passive scan seems stuck:

The helper continues when the passive queue reaches zero, stays stable for the configured period, or hits the max wait timeout.

## Git Ignore Notes

The project ignores generated and local files:

```text
target/
.env
reports/
.idea/
.DS_Store
```

Keep `.env` private because it can contain credentials.

## Current Default Behavior Summary

`mvn test` runs only the unauthenticated combined scan and writes one ZAP HTML report.

`mvn test -DsuiteXmlFile=./src/test/testng-authenticated.xml` runs only the authenticated combined scan and writes one ZAP HTML report.

API scans, alert gates, and baseline gates are available as optional single-test runs.
