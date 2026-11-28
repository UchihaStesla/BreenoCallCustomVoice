# BreenoCallCustomVoice

现代 **LSPosed / libxposed API 102** 模块（Kotlin + Jetpack Compose + **Miuix 0.9.4**），用于使 OPPO / OnePlus / realme 手机的 **小布通话（AI 接听）** 支持使用 **小布助手** 中的 **自定义声音音色**，并内置 **应用内更新拦截** 与 HyperOS/Miuix 风格设置界面。

> 逆向与目标校验基于 `com.heytap.speechassist` `v12.9.6` (`versionCode = 120906`)。

## 功能特性

- **自定义音色跨进程同步与注入**：
  - 主进程镜像 `key_cache_user_timbres` 与欢太账号鉴权信息到多进程 MMKV（`d90.b`），并复用既有 `IAiCallBridge` 提供按需 IPC 查询与云端刷新。
  - `:aiCall` 进程合并已生成（`status == 3`）的自定义音色到 `AiCallTimbreConfigBean.getToneList()`，并自动刷新小布通话音色设置页。
  - `HeytapTtsEngine.startSpeaking` 自动注入 `ttsType=USER_TIMBRE`、`userToken`、`heytapAccountDeviceId` 与 `clientContext`，内置 `biz_source` 策略（自动探测回退 / 强制清空 / 保持原样）。
  - 离线播报缺少自定义模型时自动回退至默认离线音色（`xiaoou_female`），避免无声或崩溃。
- **小布通话语音速度调节**：
  - 与小布助手一致的 `10 ~ 100` 语速刻度（默认 `50` 标准语速，步进 `5`，对应 `10 较慢 / 25 慢 / 50 标准 / 75 快 / 100 较快`）。
  - 在 `HeytapTtsEngine.startSpeaking` 及 `biz_source` 自动回退重试路径中一并注入 `speed` 参数，对内置音色与自定义音色云端播报均生效。
- **阻止小布助手应用内更新**：
  - 拦截 `UpgradeSDK` 的 `checkUpgrade`（模拟返回 `FLAG_NO_UPGRADE`，手动检查时显示「已是最新版本」）、`startDownload`、`install`、`hasDownloadComplete`。
  - 拦截 `UpgradeInfo.isUpgradeAvailable()`、`UpgradeActivity` 弹窗命令以及更新通知推送。
- **双风格现代控制面板（Miuix + Material Design 3）**：
  - 支持在 **Miuix（HyperOS 风格）** 与 **Material Design 3（Material You）** 两套原生 Compose UI 之间实时一键切换。
  - 支持 **隐藏桌面图标**（通过 `<activity-alias>` 切换，隐藏后可在 LSPosed 管理器中通过 `MODULE_SETTINGS` 入口重新打开）。
  - 实时显示模块激活状态、LSPosed 框架与 API 版本、小布助手版本检测、一键请求作用域；内置「打开小布助手」「直达小布通话设置」「小布助手应用信息」快捷入口。
  - 所有功能开关通过 `RemotePreferences` 实时下发至宿主进程，支持浅色 / 深色 / Android Monet 动态取色主题。

## 模块化项目架构

`META-INF/xposed/java_init.list` 仅注册极简入口 `HookEntry`，具体功能按职责拆分为独立模块：

```text
app/src/main/java/io/github/uchihastesla/breenocallvoice/
├── BreenoApp.kt                           # 注册 XposedServiceHelper
├── config/
│   ├── ModuleConfig.kt                    # 配置键、默认值、策略与主题枚举
│   ├── ModulePrefsReader.kt               # Hook 侧只读 RemotePreferences 封装
│   └── XposedServiceManager.kt            # UI 侧 libxposed-service 状态管理与读写
├── hook/
│   ├── HookEntry.kt                       # 极简 XposedModule 入口：进程过滤、detach()、分发子模块
│   ├── core/
│   │   ├── FeatureHook.kt                 # 独立 Hook 子模块接口契约
│   │   ├── HookContext.kt                 # 统一 Protective Hook / Origin Invoker / 日志上下文
│   │   └── ReflectExt.kt                  # 反射工具集
│   ├── bridge/
│   │   ├── BreenoConstants.kt             # 目标类名、MMKV 键、IPC 指令常量
│   │   ├── BreenoBridgeClient.kt          # 跨进程 MMKV 与 IAiCallBridge 客户端封装
│   │   ├── LauncherAllowHook.kt           # 放行模块拉起小布助手 & 直达小布通话设置
│   │   └── TimbreStore.kt                 # 线程安全自定义音色注册表与 JSON 解析
│   ├── timbre/
│   │   ├── MainTimbreSyncHook.kt          # 主进程缓存同步与云端刷新
│   │   ├── AiCallToneInjectHook.kt        # :aiCall 音色列表注入与 UI 刷新
│   │   ├── AiCallTtsEngineHook.kt         # TTS 参数注入与 biz_source 回退
│   │   └── AiCallOfflineFallbackHook.kt   # 离线播报安全回退
│   └── update/
│       └── BlockUpdateHook.kt             # 应用内自更新拦截（UpgradeSDK / 弹窗 / 通知）
└── ui/
    ├── MainActivity.kt                    # Edge-to-Edge Compose Activity
    ├── theme/AppTheme.kt                  # Miuix + Material Design 3 主题封装
    └── screen/
        ├── HomeScreen.kt                  # UI 风格路由入口
        ├── HomeScreenSupport.kt           # 共享工具与快捷跳转逻辑
        ├── miuix/MiuixHomeScreen.kt       # Miuix（HyperOS 风格）设置页
        ├── material3/Material3HomeScreen.kt # Material Design 3 设置页
        └── components/StatusCard.kt       # Miuix 状态卡片组件
```

## 构建与安装

- **GitHub Actions 自动构建**：推送到 `main` 分支或手动触发 `Build LSPosed Module APK` workflow，下载产物 `BreenoCallCustomVoice-release-apk` 即可安装。
- **使用方式**：
  1. 安装 APK，在 LSPosed 管理器中启用模块并勾选「小布助手」（或在模块 Miuix 界面中点击「一键请求小布助手作用域」）。
  2. 强行停止「小布助手」后重新打开，确认自定义音色已录制并缓存。
  3. 进入「小布通话 → 音色设置」选用你的自定义音色。
