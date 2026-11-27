# 小布通话自定义音色 (BreenoCallCustomVoice)

基于现代 **LSPosed / libxposed API 102**（`io.github.libxposed:api:102.0.0`）开发的模块，使 OPPO / 一加 / 真我手机上的 **小布通话** 支持选择并使用 **小布助手中的自定义音色**（复刻音色）。

> 适配目标：`com.heytap.speechassist`（已针对 `12.9.6` / `versionCode 120906` 实测反编译验证）。

## 使用方式

1. 安装编译好的 APK（或通过 GitHub Actions 构建下载）。
2. 在 **LSPosed 管理器** 中启用模块，作用域勾选 **小布助手 (`com.heytap.speechassist`)**（已通过 `META-INF/xposed/scope.list` 声明推荐作用域）。
3. 强行停止小布助手（含 `:aiCall` 进程）。
4. 先打开一次 **小布助手 → 设置 → 音色/自定义音色** 页面，确保自定义音色列表已缓存并同步。
5. 再进入 **小布通话设置 → 音色选择**，即可看到并试听/选用自定义音色。

## GitHub Actions 自动编译

直接将本目录推送至 GitHub 仓库即可触发 `.github/workflows/build.yml` 自动构建：
- 未配置 Secrets 时，Workflow 会自动生成临时签名证书并产出可直接安装的 Release APK（位于 Actions Artifacts 中的 `BreenoCallCustomVoice-release-apk`）。
- 如需使用固定签名，可在仓库 Settings → Secrets and variables → Actions 中配置：
  - `KEYSTORE_BASE64`（jks 证书的 base64 编码）
  - `KEYSTORE_PASSWORD`
  - `KEY_ALIAS`
  - `KEY_PASSWORD`
