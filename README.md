[README.md](https://github.com/user-attachments/files/33631667/README.md)
# AppTimeLimiter

![Platform](https://img.shields.io/badge/platform-Android-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF)
![Built with Claude](https://img.shields.io/badge/built%20with-Claude-D97757)

An Android app that limits how long you can use the apps you choose, and lets you decide **when** during the day each app is allowed. Pick an app, set a daily limit, add one or more allowed time windows (like 12:00–13:00), and the app is blocked whenever you're over the limit or outside a window.

Everything runs on the device. The app has no internet permission, no account, no analytics, and no server.

<!--
Add screenshots to docs/screenshots/ and uncomment:

| Home | Edit limits | Add apps | Blocked |
|------|-------------|----------|---------|
| ![Home](docs/screenshots/home.png) | ![Edit](docs/screenshots/edit.png) | ![Add](docs/screenshots/add.png) | ![Blocked](docs/screenshots/blocked.png) |
-->

## Features

- **Per-app daily limit.** Set it with a slider in 5-minute steps, up to 4 hours. A limit of 0 means the app is always blocked.
- **Allowed time windows.** Add several windows per app. Windows that cross midnight (for example 22:00–01:00) work. An app with no windows is allowed at any time.
- **Both rules apply together.** An app must be inside an allowed window **and** under its daily limit.
- **Blocks mid-use.** While a limited app is open, it is re-checked every 20 seconds, so you're kicked out soon after the limit or window ends.
- **Live usage.** Cards show minutes used today, a progress bar that turns red at the limit, and a "Blocked now" label.
- **Resets at local midnight.** Usage is counted from 00:00 in the phone's time zone.
## How it works

| Piece | Implementation |
|---|---|
| Detect the foreground app | `AccessibilityService` listening to window-state changes only |
| Measure time used today | `UsageStatsManager.queryEvents()` from local midnight, summing foreground and background events |
| Block an app | Full-screen `BlockActivity`; Back or "Go home" returns to the launcher |
| Store rules | `SharedPreferences` (selected apps, limits, windows) |
| UI | Jetpack Compose, Material 3 |

The core check, run when a selected app opens and every 20 seconds after:

```
if app is not in an allowed window  -> block ("outside allowed time")
else if used_today >= daily_limit   -> block ("limit reached")
else                                -> allow
```

## Permissions and privacy

| Permission | Why it's needed |
|---|---|
| **Usage access** (`PACKAGE_USAGE_STATS`) | Read how long each app was in the foreground today. Granted manually in Settings. |
| **Accessibility service** | Know which app just came to the front so it can be blocked. It only reads the package name of the window that changed. It does **not** read screen content (`canRetrieveWindowContent` is `false`), and it does not record keystrokes. || `queries` for launcher apps | Lets the app list the apps that have a launcher icon, so you can choose which to limit. |

There is no `INTERNET` permission. Nothing leaves your phone.

## Getting started

Requirements: Android Studio, a physical Android device (7.0 / API 24 or newer), and USB debugging enabled. Emulators are unreliable for usage stats and background behavior.

```bash
git clone https://github.com/PEN1TENT/AppTimeLimiter.git
```

1. Open the folder in Android Studio and let Gradle sync.
2. Run the app on your phone.
3. On the home screen, tap **Grant usage access** and turn the app on.
4. Tap **Enable blocker** and turn on the accessibility service. If the switch is greyed out ("Restricted setting"), go to Settings → Apps → AppTimeLimiter → ⋮ → **Allow restricted settings**, then try again.
5. Tap **+ Add app**, choose an app, then tap its card to set a limit and allowed times.
> **Careful when testing:** don't limit Settings, Phone, or your launcher. If you block Settings you can't open it to turn the service off. Recovery: untick the app in AppTimeLimiter, or run `adb shell pm clear com.example.apptimelimiter`.

### Samsung and other manufacturers

Phone makers often kill background apps. On Samsung (One UI), set **Settings → Apps → AppTimeLimiter → Battery → Unrestricted**, turn off "Put unused apps to sleep", and add the app to "Never auto sleeping apps". Never use **Force stop**, because Android then switches off the accessibility service. Steps for other brands: [dontkillmyapp.com](https://dontkillmyapp.com).

## Project structure

```
app/src/main/java/com/example/apptimelimiter/
├── MainActivity.kt        Compose UI: home cards, edit sheet, app picker
├── AppBlockerService.kt   AccessibilityService: detects the foreground app and blocks it
├── BlockActivity.kt       The "blocked" screen
├── UsageTracker.kt        Usage-stats permission check and today's usage per app
└── Rules.kt               Limits, time windows and storage
```

## Development milestones

The project was built in small steps, each tested on a real device before moving on.

1. App picker with persistent selection
2. Usage tracking with the Usage Access permission
3. Accessibility-based detection and block screen
4. Daily limits, with periodic re-checks while an app is open
5. Allowed time windows, including windows that cross midnight
6. Reliability on Samsung One UI (battery settings, reboot and midnight checks)
7. Minimal UI, app name and icon
## Known limitations

This is designed to curb impulse use, not to stop a determined user with full access to the phone.

- There is no PIN yet, so limits can be changed or removed in the app at any time. The accessibility service can also be switched off in Settings → Accessibility, and Android doesn't let a regular app lock that switch.
- Changing the phone's date or time can distort the usage count.
- Usage numbers come from `UsageStatsManager` and can differ slightly from Digital Wellbeing.
- Only apps with a launcher icon can be selected.
- Blocking mid-use happens on a 20-second check, so an app can stay open for up to about 20 seconds past its limit.
- Aggressive battery management on some phones can stop the service. See the manufacturer notes above.
- Google Play restricts how accessibility services may be used. This project is meant to be installed directly, and a Play Store release would need a policy declaration.

## Roadmap

- PIN protection for anything that loosens a limit
- Uninstall protection using Device Admin
- "5 minutes left" notifications
- Warn when the blocker gets switched off
- Per-day-of-week schedules
- Usage history and weekly summary

## Built with Claude

This app was planned and written step by step in conversation with [Claude](https://claude.ai), Anthropic's AI assistant, which helped with the architecture plan, the Kotlin and Jetpack Compose code, the UI design, and the Git and release checklist. I ran and tested every milestone on a Samsung A17, and decided what to build and in what order.
