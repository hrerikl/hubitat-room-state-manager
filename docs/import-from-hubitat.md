# Import Existing Code From Hubitat

Your current source of truth is the Hubitat hub browser editor. Use this once to seed the local repo, then edit locally going forward.

## Recommended Local Layout

Save the four Hubitat code entries here:

```text
apps/
  SimpleHome.groovy                      # parent app
  SimpleRoomState.groovy                 # room state child app
  SimpleRoomLighting.groovy              # room lighting child app
  SimpleModeManager.groovy               # mode manager child app
  SimpleHouseIntentLighting.groovy       # house intent lighting child app
  SimpleCircadianLighting.groovy         # circadian lighting child app
drivers/
  SimpleRoomMetaDevice.groovy            # device driver, later
```

Keeping apps in `apps/` and drivers in `drivers/` lets Gradle compile them without extra configuration.

## Copy From Hubitat

1. Open your Hubitat admin UI.
2. Go to **Apps Code**.
3. Open `Simple Home`.
4. Select all source, copy it, and paste it into `apps/SimpleHome.groovy`.
5. Repeat for the child apps listed above.
7. Later, when you are ready, copy `SimpleRoomMetaDevice` from **Drivers Code** into `drivers/SimpleRoomMetaDevice.groovy`.

After copying, run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\gradle.ps1 check
```

## Local To Hubitat Workflow

Normal deployment now goes through HPM and the generated package manifest:

1. Edit locally.
2. Run `check`.
3. Run `releaseHubitatPackage` with the next version and release notes.
4. Commit and push.
5. Run HPM update on the hub.

The shared helper library is included in `packageManifest.json` and the package ZIP, so it no longer needs a manual copy/paste update before HPM.
