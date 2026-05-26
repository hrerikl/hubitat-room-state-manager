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

For now, the safest deployment loop is still manual:

1. Edit locally.
2. Run `check`.
3. Copy the changed file back into the matching Hubitat **Apps Code** or **Drivers Code** editor.
4. Save.

Once the code is safely in Git, a future step can add a scripted uploader that talks to your hub's editor endpoints. That is convenient, but it is worth doing after the first local import so we have a known-good baseline.
