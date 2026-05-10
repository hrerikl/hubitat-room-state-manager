# Hubitat Groovy App Dev Environment

This workspace gives you a local development loop for Hubitat Groovy apps without living entirely in the Hubitat browser editor.

## What is included

- `apps/SimpleRoomStateManager_v2.groovy` - parent app source copied from your hub.
- `apps/SimpleRoomStateChild_v2.groovy` - room state child app source copied from your hub.
- `apps/RoomLightingTemplateBuilder_v2.groovy` - lighting template child app source copied from your hub.
- `drivers/` - Hubitat driver source copied from your hub.
- `src/main/groovy/apps/RoomStateAutomation.groovy` - starter app source that can be deleted once your real code is imported.
- `src/test/groovy/apps/RoomStateAutomationSpec.groovy` - Spock tests.
- `src/test/groovy/hubitat/HubitatAppSpec.groovy` - lightweight Hubitat runtime harness.
- `config/codenarc/codenarc.groovy` - conservative lint rules for dynamic Hubitat Groovy.
- `build.gradle` - Groovy, Spock, CodeNarc, and test tasks.

## Import Your Current Hubitat Code

Your project currently lives on the hub. Follow [docs/import-from-hubitat.md](docs/import-from-hubitat.md) to copy the three app entries now; the driver slot is ready for later.

## Requirements

- Java 11 or newer.
- Gradle, unless you add a wrapper with `gradle wrapper`.
- VS Code, IntelliJ IDEA, or another editor with Groovy support.

Check your setup:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-dev-env.ps1
```

On Windows, a quick install path is:

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
winget install Gradle.Gradle
```

After installing, reopen your terminal so PATH changes are picked up.

## Common Commands

Run tests:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 test
```

Run lint:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 codenarcMain codenarcTest
```

Run everything:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 check
```

In VS Code, use **Terminal > Run Task** and pick `Hubitat: check`, `Hubitat: test`, or `Hubitat: lint`.

## Hubitat Workflow

1. Edit app code locally in `src/main/groovy/apps/RoomStateAutomation.groovy`.
2. Run `gradle check`.
3. Copy the app source into Hubitat: **Apps Code > New App** or your existing app entry.
4. Save and install/update the app from **Apps**.

Hubitat app code is script-like Groovy, so the test harness intentionally mocks only the platform calls this app uses. As your app grows, add methods to `src/test/groovy/hubitat/HubitatAppSpec.groovy` instead of pulling Hubitat-specific behavior into production code.

## Suggested Next Steps

- Paste your current Room State app into `src/main/groovy/apps/RoomStateAutomation.groovy`.
- Add tests around your real state transitions.
- Add any Hubitat methods you use to the harness as small mocks.
- Optional: add a deployment script once you decide how you want to authenticate against your hub.
