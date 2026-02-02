# Security Testing Automation

A small Java/Maven project that automates OWASP ZAP scans together with Selenium-based browser interactions.

## Project highlights

- Automates ZAP (zap-clientapi) scans driven by TestNG tests.
- Uses Selenium + WebDriverManager to drive Chrome through the ZAP proxy.
- Environment-configurable (ZAP host/port/api key, application base URL).
- Generates timestamped HTML reports stored in `reports/`.

## Quick setup

Prerequisites

- Java 17 (JDK)
- Maven
- Chrome browser
- OWASP ZAP (daemon or desktop) with API enabled and reachable

Environment variables (or use a `.env` file)

- `ZAP_ADDRESS` — ZAP host (default: `localhost`)
- `ZAP_PORT` — ZAP API port (e.g. `8080`)
- `ZAP_API_KEY` — ZAP API key (if required)
- `APP_BASE_URL` — target application base URL (example: `https://example.com`)
- Optional: `CHROME_HEADLESS=true` to run Chrome headless

Example `.env` snippet

ZAP_ADDRESS=localhost
ZAP_PORT=8080
ZAP_API_KEY=changeme
APP_BASE_URL=https://ginandjuice.shop
CHROME_HEADLESS=true

Build & run

- Install dependencies and run tests: `mvn test`
- The TestNG suite used: `src/test/testng.xml`.

## Project structure (top-level)

- `pom.xml` — Maven build + dependencies (TestNG, zap-clientapi, Selenium, WebDriverManager, dotenv)
- `src/main/java/utils/ZapUtil.java` — helper utilities for interacting with ZAP
- `src/test/java/ZapTest.java` — TestNG test that exercises the application via ZAP proxy
- `src/test/testng.xml` — TestNG suite configuration
- `reports/` — generated HTML ZAP reports (timestamped files)
- `target/` — Maven build output

## Running tests & locating reports

- Execute: `mvn test` (uses the TestNG suite configured in the project)
- After a run an HTML report is created under `reports/` with a timestamped filename (e.g. `27-01-2026_15-28-22_ZAP-Report-<host>_.html`).

## Troubleshooting

- If the test cannot connect to ZAP: verify `ZAP_ADDRESS` and `ZAP_PORT`, ensure ZAP is running and API access is enabled.
- If API calls fail: check `ZAP_API_KEY` (set or disable API key requirement in ZAP for local runs).
- If Chrome fails to start: ensure Chrome is installed and WebDriverManager can download a compatible driver (network access required).
- Inspect `target/test-classes` and the `reports/` folder for generated outputs and logs.

## Next steps / suggestions

- Add an example `.env.example` checked into the repo (without secrets).
- Add CI integration to run scans and archive reports (GitHub Actions, GitLab CI).
- Expand tests to cover authentication flows and more pages.

