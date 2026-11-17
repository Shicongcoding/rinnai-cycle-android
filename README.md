# 林内循环 · Android App

通过 MQTT 控制林内热水器的「一键循环」，到时自动关闭。新增 **NFC 贴标签自动启动 1 分钟循环**。

## 构建

```bash
export JAVA_HOME=<JDK 21 路径>
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 使用流程

### 1. 首次配置

打开 App，展开「设备设置」，填入手机号、登录密码、设备 MAC、classID、authCode，保存。

这些参数来自你自己账号下的林内设备，最快的方式是在官方 App 的设备详情里查看 MAC / 型号；classID 与 authCode 可通过官方 App 的设备列表接口获取：

- `GET https://iot.rinnai.com.cn/app/V1/login` 获取 token
- `GET https://iot.rinnai.com.cn/app/V1/device/list` 获取 `mac`、`classID`、`authCode`、`online`

登录参数：`username`=手机号、`password`=MD5(登录密码) 大写、`identityLevel`=0、`appType`=1、`appVersion`=3.12.0。设备列表请求需带 `Authorization: Basic {token}`。

### 2. 写入 NFC 触发标签

1. 准备一张可写的 NFC 标签（NTAG213/215/216 等均可）。
2. 点击「写入 NFC 触发标签」，屏幕提示「请将手机贴近 NFC 标签…」。
3. 将手机贴近标签，写入成功后会提示「写入成功！贴标签即可启动循环」。

标签里写入的是一条 NDEF URI 记录：`rinnai://cycle`。

### 3. 贴标签自动循环

把这张标签贴到热水器旁。之后**手机贴近标签**，系统自动打开 App 并启动 **1 分钟循环**，到点自动关闭。

- 手机息屏/后台：靠系统 `NDEF_DISCOVERED` 匹配 `rinnai://cycle`，自动唤起 App。
- App 已在前台：通过 `onNewIntent` 直接触发。
- 配置未填全时贴标签，会提示「请先在 App 内填写设备配置」，不会误启动。

### 4. 手动控制

App 主界面仍保留「开始循环」「立即关闭」按钮，可手动设定任意时长，与 NFC 触发互不影响。

## 实现要点

| 文件 | 说明 |
| --- | --- |
| `AndroidManifest.xml` | NFC 权限、`NDEF_DISCOVERED` intent-filter（匹配 `rinnai://cycle`）、`singleTop` |
| `MainActivity.java` | NFC 触发检测、NFC 标签写入、手动控制入口 |
| `CycleService.java` | 前台服务：开循环 → 倒计时 → 自动关循环 |
| `RinnaiMqtt.java` | MQTT over TLS 收发控制报文 |
| `Config.java` | SharedPreferences 存储账号与设备参数 |

NFC 触发固定使用 1 分钟（`NFC_CYCLE_MINUTES = 1`），如需调整改 `MainActivity.java` 中该常量即可。
