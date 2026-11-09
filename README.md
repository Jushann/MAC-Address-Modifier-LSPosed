# WifiMacCurrent · 当前 Wi-Fi MAC

面向 LSPosed 的当前 Wi-Fi MAC 临时切换模块，支持实验性 Wi-Fi 7 MLO、连接详情、主题配色及深色模式。

An experimental LSPosed module for changing the active Wi-Fi station MAC, with MLO support, network diagnostics, and configurable light/dark themes.

**当前版本：0.6.0 · MLO 实验版 · MIT License**

## 功能

- 大字显示当前系统读回 MAC，支持复制。
- 手动输入或随机生成有效单播地址，仅对当前主 Wi-Fi 配置生效。
- 首次加载 Hook 需重启；之后应用地址通过 Wi-Fi 断线重连完成，无需重启手机。
- MLO 分别展示设备 MLD 地址与驱动派生的链路地址。
- Wi-Fi 详情：802.11 标准、频段/信道、信号强度、协商速率、认证方式、SSID/BSSID、IPv4/IPv6、子网掩码、网关、DNS、MTU、代理和连接状态。
- 按需查询公网 IPv4/IPv6；分别提供默认出口查询和明确标注的 Wi-Fi 直连查询。
- 靛蓝、海蓝、青绿、紫罗兰、玫瑰五套配色；跟随系统、浅色、深色三种显示模式。

模块通过系统 Wi-Fi / HAL 路径请求更改当前接口地址，不只是替换应用读取到的字符串；不会永久改写网卡出厂地址。

## 兼容性与限制

已在 **Redmi K70 Pro / Android 16 / HyperOS 3 / KernelSU / LSPosed 2.0.2 (7668)** 上验证。APK 最低 Android 13，但不代表所有 Android 13+ 设备均兼容。

Hook 使用 ROM 内部 Wi-Fi API；其他机型、系统版本和驱动可能拒绝请求或改变接口。当前仅适配普通个人 Wi-Fi，不支持企业 EAP / Passpoint。尚未验证所有 MLO 链路组合、长期漫游或任意厂商固件。

MLO 设备地址与每条链路地址可能不同。驱动接受请求后仍应检查系统读回、连通性与路由器在线列表。无线协商速率不是实际下载速度。

## 安装与使用

1. 从 [Releases](https://github.com/Jushann/WifiMacCurrent/releases) 下载 APK 并安装。
2. 在 LSPosed 中启用“当前 Wi-Fi MAC”，作用域选择 **系统框架**。首次安装或更新 Hook 后重启一次。
3. 连接 Wi-Fi，打开“地址”页，填写或生成 MAC，点击“应用并重连”。应用时会短暂断网。
4. 在路由器在线列表核对地址。要恢复时进入“设置 → 恢复并重连”。
5. “Wi-Fi 详情”页查看网络参数；“设置 → 外观”切换主题。

当前版本沿用 0.3 的 Hook，从 0.3 或更新版本升级到 0.6 无需为界面变更重启。LSPosed 仍可能显示通用的重启提醒。

临时规则仅匹配当前主 Wi-Fi 的网络 ID 与配置标识，同网络重连保留；切换其他主网络或重启后清除。双 Wi-Fi 的另一个独立网络不在修改范围。地址不能与同网设备重复；驱动可能拒绝部分合法地址。

## 权限与网络请求

- 系统控制广播要求本应用的签名权限。
- Wi-Fi / 网络状态权限用于本地详情；位置及附近设备权限用于读取完整 Wi-Fi 标识。系统定位关闭或未授权时，SSID/BSSID 可能被隐藏。
- 公网查询只在点击时进行，通过 HTTPS 请求 ipify，失败后使用 ipw.cn，IPv4 另有 myip.ipip.net 备用服务。请求不包含 SSID、MAC 或设备标识，服务可看到出口 IP。
- 默认查询遵循应用默认网络路由。Wi-Fi 直连按钮显式绑定 Wi-Fi，可能绕过 VPN；透明代理、上游代理与 NAT 仍会影响出口。不能据此获取代理或 NAT 后不可观察的“绝对真实 IP”。
- 不修改 Wi-Fi 密码、配置 XML 或 persist 分区。主题偏好仅保存在本机。

## 构建

当前使用 Windows PowerShell 脚本直接调用 Android SDK 工具，不依赖 Gradle。

准备 JDK 17、Android SDK Platform 36、Build Tools 36.0.0，以及 [Xposed API 82](https://api.xposed.info/) JAR（只作编译依赖，不打包进 APK）。官方 Maven 坐标为 `de.robv.android.xposed:api:82`，仓库 `https://api.xposed.info/`。

在源码根目录运行，按实际安装位置调整参数：

```powershell
New-Item -ItemType Directory -Force dist | Out-Null
.\build.ps1 `
  -AndroidJar "$env:LOCALAPPDATA\Android\Sdk\platforms\android-36\android.jar" `
  -BuildTools "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0" `
  -XposedApi 'C:\deps\api-82.jar' `
  -Jdk 'C:\Program Files\Java\jdk-17' `
  -OutputApk "$PWD\dist\WifiMacCurrent-0.6.0.apk"
```

脚本会在 `build/` 生成本地测试签名密钥。这是开发用途的签名流程；私钥不随源码发布。自行构建产生的签名通常不同，不能直接覆盖安装已有 Release APK。不要提交或公开自己的签名密钥。

独立规则测试（JDK 在 PATH 中）：

```powershell
javac -encoding UTF-8 -d test-classes src/dev/local/wifimac/MacRules.java src/dev/local/wifimac/SessionRule.java tests/RulesTest.java
java -cp test-classes RulesTest
```

## 源码结构

| 文件 | 用途 |
| --- | --- |
| `Hook.java` | 系统 Wi-Fi Hook、受签名权限保护的控制接口 |
| `MacRules.java` / `SessionRule.java` | 地址校验与当前网络匹配 |
| `MainActivity.java` | 地址和设置页面 |
| `WifiDetailsView.java` | Wi-Fi 详情与公网查询 |
| `ThemePalette.java` | 配色、深色模式与偏好保存 |

Java 源文件位于 `src/dev/local/wifimac/`。验证范围见 [测试说明](docs/TESTING.md)，版本变化见 [CHANGELOG](CHANGELOG.md)。

## 问题反馈

请在 Issues 中提供设备型号、Android / ROM / LSPosed 版本、复现步骤，以及脱敏后的 `WifiMacCurrent` 日志。不要公开真实 SSID、MAC、IP、密码、签名私钥或完整系统日志。

## 许可证与参考

本项目代码采用 [MIT License](LICENSE)。Android SDK、Xposed API 和其他外部工具遵循各自许可证，不包含在源码仓库中。

- [Android Wi-Fi 7 / MLO](https://source.android.com/docs/core/connect/wifi-7)
- [Android WifiInfo](https://developer.android.com/reference/android/net/wifi/WifiInfo)
- [Xposed API](https://api.xposed.info/)
