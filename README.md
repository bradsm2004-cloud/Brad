# Custom Start Stopwatch for Android

A native Android stopwatch with a Samsung-inspired dark interface and an adjustable starting time.

## Features

- Set the stopwatch's starting hours, minutes, seconds, and hundredths
- Start, pause, resume, and reset
- Record unlimited laps
- View each lap's split and total time
- Fastest lap in green and slowest lap in red
- Accurate timing based on Android's monotonic clock
- Continues measuring accurately when the app is briefly in the background
- Keeps the screen awake while the stopwatch is running
- Preserves the timer and lap list across screen rotation
- Shows an ongoing stopwatch on the lock screen and notification panel
- Includes pause, resume, lap, and reset controls in the notification
- Continues running when the app is removed from Recent apps
- Restores the running stopwatch and saved laps after Android recreates the app
- Shares all lap results and copies individual laps with a long press

## Build the APK

1. Open this folder in Android Studio.
2. Let Android Studio finish the Gradle sync and install Android SDK 35 if prompted.
3. Choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Android Studio will place the APK under `app/build/outputs/apk/debug/`.

The app supports Android 8.0 (API 26) and newer.

## Build online without installing Android Studio

1. Create a free GitHub repository with `main` as its default branch.
2. Upload the contents of this project to the repository.
3. Open the repository's **Actions** tab and select **Build Stopwatch APK**.
4. When the run finishes, open it and download **Custom-Start-Stopwatch-APK** from the Artifacts section.
5. Unzip the downloaded artifact to get `app-debug.apk`.
