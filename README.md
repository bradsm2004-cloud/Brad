# Custom Start Stopwatch for Android

A native Android stopwatch with a Samsung-inspired dark interface and an adjustable starting time.

## Features

- Set the stopwatch's starting hours, minutes, seconds, and hundredths
- Start, pause, resume, and reset
- Record unlimited laps
- See a live current-lap timer beneath the overall stopwatch time
- View each lap's split and total time
- Fastest lap in blue and slowest lap in red
- Accurate timing based on Android's monotonic clock
- Continues measuring accurately when the app is briefly in the background
- Optional keep-screen-awake setting
- Optional vibration confirmation when a lap is recorded
- Confirmation before a reset can erase the session
- Preserves the timer and lap list across screen rotation
- Shows an ongoing stopwatch on the lock screen and notification panel
- Requests Android's promoted Live Update stopwatch chip on supported phones
- Includes pause, resume, lap, and reset controls in the notification
- Continues running when the app is removed from Recent apps
- Restores the running stopwatch and saved laps after Android recreates the app
- Shares or copies all lap results from the options menu and copies individual laps with a long press
- Uses status-bar insets so the top controls remain easy to tap

## Build the permanently signed APK with GitHub

The repository must contain these four encrypted Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Never upload the private `.jks` signing key or its backup files to the repository.

After uploading the project, open **Actions > Build Stopwatch APK**. A successful run produces the artifact **Custom-Start-Stopwatch-v3-Signed-APK**, which contains `app-release.apk`.

The app supports Android 8.0 (API 26) and newer. Live Update promotion depends on the phone's Android/One UI version and notification settings; the standard lock-screen and notification-panel display remains available.
