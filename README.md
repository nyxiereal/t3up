# T3 Updater

T3 Updater is an unofficial Android app, package `meow.nyx.t3up`, that updates the T3 Code Android app, package `com.t3tools.t3code`, from nightly releases at [pingdotgg/t3code](https://github.com/pingdotgg/t3code/releases). It is not affiliated with T3 or Ping.

## Requirements

- Android 12 or newer, with `minSdk` 31.
- Root access, tested with KernelSU. Grant root access to T3 Updater in your root manager.

## How it works

The GitHub release tag, such as `v0.1.12-nightly.20261123.5124`, differs from the Android version. T3 Updater reads the `versionCode` from the APK asset name, such as `t3code-android-internal-2.3.0-815.apk`, and compares it with the installed `versionCode`. Several nightlies can ship the same APK.

When a newer `versionCode` is available, the app downloads the APK and verifies its size and SHA-256 digest against the digest GitHub reports for the asset. It then installs the APK silently with `pm install -r` via `su`.

The "Auto-update" toggle schedules a `JobScheduler` job every hour. The job runs only on an unmetered network such as Wi-Fi when battery is not low, and it persists across reboots. Manual "Update now" runs work on any network.

The optional GitHub personal access token field avoids the 60 requests per hour anonymous API limit. T3 Updater sends the token only to the GitHub releases API and stores it in plain text in its private `SharedPreferences`.

## Install

Download the APK from this repository's [Releases page](https://github.com/nyxiereal/t3up/releases).

## Build

Building needs the Android SDK with platform `android-37.2`.

```sh
./gradlew assembleRelease
```

Gradle writes the APK to `app/build/outputs/apk/release/app-release.apk` and signs the release build with the local debug key.

## Known limits

- Installing an update closes T3 Code if it is open.
- Android may delay background jobs under Doze, so one hour is a minimum interval.
