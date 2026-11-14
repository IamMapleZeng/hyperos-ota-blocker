# HyperOS OTA Blocker

[简体中文](README.md) · **English**

An LSPosed module built with Modern Xposed API 102, with a Kotlin and Jetpack Compose Material 3 settings app. All switches are off by default.

## Version 0.1.0 compatibility

- **Device and OS.** Support is limited to Xiaomi 15 Ultra (`xuanyuan`) running HyperOS `OS4.0.3.0.XOACNXM` on Android 17. The module checks the full build fingerprint.
- **System updater.** The module supports the specific APK of `com.android.updater` **9.7.5 (975)** listed below. The APK's SHA-256 must also match.
- **LSPosed.** The framework must support **Modern Xposed API 102**, which is available in LSPosed **2.2.1 (7912)**. Enable the module in LSPosed Manager, select only `com.android.updater` in its scope, and reopen the settings app.
- **Disabled switches.** The factory updater **9.7.4 (974)** is currently unsupported. A device with that version shows “Build unsupported” and disables the blocking switches even when its OS matches and LSPosed is connected. Other unmatched builds or updater APKs have the same restriction.

## Xiaomi 15 Ultra compatibility evidence

The updater APK was pulled from an authorized device via ADB. Android SDK tools `aapt2` and `dexdump` were used to inspect the manifest, signature-level permissions, method descriptors, and call paths. The hooks still require on-device runtime testing.

| Item | Observed value |
| --- | --- |
| Model | 25019PNF3C / xuanyuan |
| System | HyperOS `OS4.0.3.0.XOACNXM`, Android 17 / SDK 37, build `CP2A.260605.016` |
| Build fingerprint | `Xiaomi/xuanyuan/xuanyuan:17/CP2A.260605.016/OS4.0.3.0.XOACNXM:user/release-keys` |
| Current updater | `com.android.updater` 9.7.5 / 975, an updated system app |
| APK path | `/data/app/~~7WitI6kQj3PT8xP-KijAgg==/com.android.updater-ShpfRZKcIcsegULtjv1-qA==/base.apk` |
| Factory updater | `/product/app/Updater`, 9.7.4 / 974 |
| Updater APK SHA-256 | `7f4f3fc09d44a6d9140b0e986f6e1ed1cd054f662d8aba29a191b95e4bbf8f44` |
| OTA services | A/B with `sys.ota.type=update_engine`. `update_engine_lazy` was stopped during inspection. The system registered `system_update` and `recovery`, and the updater declared `UpdateService`. |
| LSPosed | `org.lsposed.manager` 2.2.1 / 7912. `com.sukisu.ultra` was installed. |

The standard Android system-update intent resolves to Google Play services on this device. Xiaomi's `miui.intent.action.CHECK_UPDATE` resolves to `com.android.updater/.MainActivity`. The settings app displays the framework name, version, build number, and API level reported by the connected framework. [LSPosed release channel](https://t.me/s/LSPosed) · [libxposed API](https://libxposed.github.io/api/)

### Hook coverage

- **Update checks.** `A.b()` requests `/updates/miotaV3.php`. When blocked, the method returns its existing null result and `A.run()` still delivers the completion callback. Daily checks use `DailyCheckReceiver` to send command 13 to `UpdateService`. The request can also carry updater data such as XMS.
- **ROM downloads.** `C.i0(String,String)` calls `DownloadManager.enqueue()`. The switch covers new ROM downloads through this entry point and the streaming A/B branch with a non-null `Payload`. Previously submitted jobs may continue. APEX, COTA, XMS, and cold patch downloads have separate paths.
- **Installation.** The module blocks both A/B submission branches in `UpdateEngineClient.updatePackage(...)` and returns the failure result handled by its caller. It also blocks the OTA-specific reboot in `K$b.run()`. Manual A/B installs using the same entry point are covered. Legacy Recovery installs and previously submitted A/B tasks are outside this coverage.
- **Reminders.** The module filters selected OTA notifications, including background new-version reminders, progress notifications, and notification IDs 3 and 4. It also covers dialog types 1 and 2 in the updater's launcher reminder paths. Existing notifications may require manual dismissal.
- **Hook registration and settings.** Registration requires the updater's main process, system-app status, the full build fingerprint, and matching hashes for all APKs. When the master switch is off or settings cannot be read, the original method runs. LSPosed Remote Preferences stores the five switches as a single snapshot.
- **Logs.** The settings app shows the last 100 events from its own process and exports diagnostics through Android's file picker. LSPosed Manager logs hook registration and the first block at each entry point under `HyperOtaBlocker`.

## Build on macOS

Install SDK Platform 37.0, Build Tools 35.0.0, and Platform Tools through the [Android Studio SDK Manager](https://developer.android.com/studio). Use JDK 17. The Gradle Wrapper pins Gradle 8.11.1 and verifies its distribution ZIP. libxposed 102.0.0 requires compile SDK 37. This project uses AGP 8.9.2 and targets SDK 35. The SDK preparation script creates a compatible integer-version platform directory alongside the SDK Manager installation.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
java -version
adb version
adb devices -l
./scripts/prepare-sdk37.sh
./gradlew assembleDebug testDebugUnitTest
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify app/build/outputs/apk/debug/app-debug.apk
```

The device collection script reads device information and saves it locally under `work/`. Run it without arguments to discover candidate packages. Pass the confirmed package name to pull its APK.

```bash
./scripts/collect-device.sh
./scripts/collect-device.sh com.android.updater
```

## Enable and restore

1. Install the APK, enable **HyperOS OTA Blocker** in LSPosed Manager, and select only **com.android.updater** in the module scope.
2. Restart the updater process. Open the module settings to check the framework API and compatibility status, then enable the master switch and the desired individual switches.
3. Turn the master switch off to allow subsequent calls once the new settings reach the process. The restore action resets all switches. Restart the updater if it continues to show stale state. Android manages existing files and previously submitted OTA jobs.
4. If the updater cannot start, disable the module in LSPosed Manager, force-stop the updater from Android's app settings, and reopen it. If LSPosed Manager is unavailable but the launcher is accessible, uninstall the module and reboot. If the launcher is also inaccessible, use the safe-mode procedure for your SukiSU Ultra installation and disable LSPosed before booting normally.

## Tests and build status

- Five JVM tests passed. They cover all 32 switch combinations, defaults, the master switch, reset behavior, the streaming and local install branches, and OTA notification ID filtering.
- Use synthetic update data and callbacks for hook tests. Keep real OTA URLs and payloads out of the system services. Before offline device testing, check for pending downloads, installs, and reboots.
- Runtime hook behavior, cross-process preference propagation, updater cold starts, and recovery after reboot still need device testing.
- The Debug build and unit tests ran on macOS arm64 with JDK 17.0.16 and Gradle 8.11.1. The signed release APK passed signature verification. Release Lint was skipped during offline packaging because its dependencies were unavailable.
- The early Debug build used `dev.ota.blocker`. The LSPosed release uses `io.github.iammaplezeng.hyperosotablocker`, version 0.1.0 (code 1). Android installs it as a separate app with its own settings and LSPosed scope.
- Release APK SHA-256 is `2d4afeac51f190ae09ac0acf68e66de527e2344044388f8a79fed0ace1f34123`. [Download the release](https://github.com/Xposed-Modules-Repo/io.github.iammaplezeng.hyperosotablocker/releases/tag/1-0.1.0)
