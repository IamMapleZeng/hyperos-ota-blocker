# HyperOS OTA Blocker

**简体中文** · [English](README.en.md)

Kotlin / Jetpack Compose Material 3 设置应用与 LSPosed Modern API 102 模块。所有开关默认关闭。

## 0.1.0 版本要求

- **系统**。当前仅适配小米 15 Ultra（`xuanyuan`）的 HyperOS `OS4.0.3.0.XOACNXM`，Android 17。模块会核对完整系统构建指纹。
- **系统更新器**。仅适配 `com.android.updater` **9.7.5（975）** 的指定 APK。模块同时校验 APK 的 SHA-256，因此相同版本号也需要 APK 内容一致。
- **LSPosed**。框架需支持 **Modern Xposed API 102**，已确认 LSPosed **2.2.1（7912）** 提供该 API。在管理器中启用模块，作用域仅选择 `com.android.updater`，然后重新打开模块设置页。
- **开关变灰**。原厂预装的更新器 **9.7.4（974）** 尚未适配。即使系统构建匹配、LSPosed 已连接，安装了 9.7.4 的设备仍会显示「未适配此构建」，拦截开关不可用。其他未匹配的构建或更新器也会受到同样限制。

## 小米 15 Ultra 静态适配

本次通过已授权的 ADB 读取真机并拉取当前更新器 APK，使用 Android SDK 的 `aapt2` 和 `dexdump` 检查清单、签名级别权限、方法描述符与调用分支。此版本的 Hook 仍需经过真机执行测试。

| 项目 | 设备读数 |
| --- | --- |
| 型号 | 25019PNF3C / xuanyuan |
| 系统 | HyperOS `OS4.0.3.0.XOACNXM`，Android 17 / SDK 37，构建 `CP2A.260605.016` |
| 构建指纹 | `Xiaomi/xuanyuan/xuanyuan:17/CP2A.260605.016/OS4.0.3.0.XOACNXM:user/release-keys` |
| 当前系统更新器 | `com.android.updater` 9.7.5 / 975，更新后的系统应用 |
| APK 路径 | `/data/app/~~7WitI6kQj3PT8xP-KijAgg==/com.android.updater-ShpfRZKcIcsegULtjv1-qA==/base.apk` |
| 原厂版本 | `/product/app/Updater`，9.7.4 / 974 |
| APK SHA-256 | `7f4f3fc09d44a6d9140b0e986f6e1ed1cd054f662d8aba29a191b95e4bbf8f44` |
| OTA 服务 | `sys.ota.type=update_engine`，A/B，读取时 `update_engine_lazy=stopped`；系统注册了 `system_update` 和 `recovery`，更新器声明了 `UpdateService` |
| LSPosed | `org.lsposed.manager` 2.2.1 / 7912；`com.sukisu.ultra` 已安装 |

通用 `android.settings.SYSTEM_UPDATE_SETTINGS` 在设备上指向 GMS。小米入口 `miui.intent.action.CHECK_UPDATE` 属于 `com.android.updater/.MainActivity`。LSPosed 团队从 2.1.0 起支持 API 102。7912 对应其公开版 2.2.1；已安装 ZIP 的原始下载位置尚未验证，设置页连接后会显示框架自报名称、版本、构建号与 API。参见 [LSPosed 发布频道](https://t.me/s/LSPosed)、[7912 构建讨论](https://github.com/JingMatrix/Vector/issues/1050) 和 [libxposed API](https://libxposed.github.io/api/)。

### Hook 路径与覆盖范围

- 检测。更新器的 `A.b()` 请求 `/updates/miotaV3.php`，开关开启时返回该方法原有的空结果。其调用者 `A.run()` 会继续传递检查完成回调。后台每日任务由 `DailyCheckReceiver` 向 `UpdateService` 投递命令 13。此检查请求也可能返回 XMS 等更新器数据。
- 下载。ROM 下载入口 `C.i0(String,String)` 调用 `DownloadManager.enqueue()`，覆盖经该入口提交的新 ROM 下载。流式 A/B 的非空 `Payload` 分支也受此开关控制。已提交给下载服务或 UpdateEngine 的任务可能继续运行。独立的 APEX、COTA、XMS 和冷补丁下载使用各自路径。
- 安装。拦截应用内部 `UpdateEngineClient.updatePackage(...)` 的两种 A/B 提交分支，并返回调用者已处理的失败结果。拦截 `K$b.run()` 的 OTA 专用重启调用。该开关也会拦截更新器内手动发起、但使用相同入口的 A/B 安装。传统 Recovery 安装路径和已提交的 A/B 任务不在覆盖范围内。
- 提醒。拦截更新器的指定 OTA 提醒方法，包括后台新版本提醒、OTA 进度、通知编号 3 和 4，以及 `LauncherDialogReceiver` 与桌面提示分发的类型 1、2。已发布通知可能保留至手动清除。未经过这些入口的系统提醒可能仍会显示。
- 生效条件。只有更新器主进程、系统应用标志、完整构建指纹和全部 APK 哈希匹配时才注册 Hook。总开关或配置读取失败时执行原方法。配置保存在 LSPosed Remote Preferences，五个开关以一个原子快照提交。系统更新器、Google Play、Bootloader、AVB 和系统分区无需改动。
- 日志。应用内显示设置进程最近 100 条事件，诊断文本通过系统文件选择器导出。更新器进程的 Hook 安装及每个入口的首次拦截记录在 LSPosed 管理器日志，标签为 `HyperOtaBlocker`。

## macOS 构建

通过 [Android Studio](https://developer.android.com/studio) 的 SDK Manager 安装 SDK Platform 37.0、Build Tools 35.0.0 和 Platform Tools，另准备 JDK 17。Gradle Wrapper 固定为 8.11.1 并校验发行 ZIP。libxposed 102.0.0 要求编译 SDK 37。项目使用 AGP 8.9.2 并将 targetSdk 固定为 35。AGP 8.9.2 识别整数平台路径，`prepare-sdk37.sh` 为它创建 SDK 37.0 的兼容副本，保留 SDK Manager 安装的原始目录。

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

真机信息采集脚本 `scripts/collect-device.sh` 只读取手机并将结果保存到本地 `work/`。首次运行用于发现包，确认后可传入包名以导出 APK。

```bash
./scripts/collect-device.sh
./scripts/collect-device.sh com.android.updater
```

## LSPosed 启用与恢复

1. 经确认后安装 Debug APK。在 LSPosed 管理器中选择 **HyperOS OTA Blocker**，启用模块。作用域只勾选系统更新器 **com.android.updater**。
2. 重新启动更新器进程。打开本模块查看 LSPosed API 和适配状态，按需启用总开关和单项开关。
3. 关闭总开关后，后续 Hook 调用读到新配置即放行。点「恢复系统更新 / 默认设置」可同时清空所有开关。若更新器仍显示旧状态，关闭并重新启动更新器。已提交的 OTA 任务和更新器已有文件由系统管理。
4. 更新器无法启动时，先在 LSPosed 管理器禁用本模块，随后在应用信息中强行停止更新器，再重新打开。若管理器不可用而系统可进入桌面，卸载本模块 APK 后重新启动手机。若桌面也无法进入，按当前 SukiSU Ultra 安装方式进入其模块安全模式并禁用 LSPosed，再正常开机。
5. 调试安装、启用作用域、停止应用与重启需要用户确认。本次真机检查仅执行了 ADB 读取。

## 测试计划

- 本机 JUnit 测试覆盖全部 32 种开关组合、默认值、总开关、恢复语义、流式与本地安装分支和 OTA 通知编号过滤。
- 测试前确认系统没有正在下载、安装或等待重启的更新。用调试夹具提供合成版本信息与回调，记录下载或安装分发调用次数，不把 URL 或 payload 交给真实系统服务。切换单项开关分别核对检测、ROM 入队、流式分发、A/B 提交、OTA 通知和指定弹窗；同时核对普通通知和手动重启入口。
- 在真机分阶段验证模块启用与禁用、更新器冷启动、Remote Preferences 跨进程同步、设置进程重启和恢复默认。真机上先断开外网，确认没有待安装任务后再打开更新器核对 Hook 日志。重启测试须独立确认并检查待安装状态。
- Hook 生效、通知覆盖、安装拦截、下载拦截、设置持久化和重启恢复均待真机执行验证。检测恢复测试以原方法执行与回调为准，保持 OTA 下载与安装禁用或使用离线夹具。

## 本次构建结果

macOS arm64，JDK 17.0.16，Gradle 8.11.1。已完成 `assembleDebug` 和 `testDebugUnitTest`，5 项 JUnit 测试全部通过。APK 已通过 `apksigner verify`，并检查了 Modern API 102 的入口、固定作用域与包名。实际 Hook 执行、开关跨进程传播及重启恢复仍待用户授权后的真机测试。

- 早期调试版的包名为 `dev.ota.blocker`。
- LSPosed 投稿版的包名为 `io.github.iammaplezeng.hyperosotablocker`，版本为 `0.1.0`（版本代码 `1`）。Android 会将它作为独立应用安装，其开关和 LSPosed 作用域需要重新设置。
- 投稿 APK 文件名为 `HyperOS-OTA-Blocker-0.1.0-release.apk`，SHA-256 为 `2d4afeac51f190ae09ac0acf68e66de527e2344044388f8a79fed0ace1f34123`。投稿版已通过 APK 签名校验。离线打包时跳过了缺少依赖的 Release Lint。
