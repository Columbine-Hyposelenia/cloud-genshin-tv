# 云·原神 TV — 完整知识库 / 上下文恢复手册

> 目的：即使对话上下文完全丢失，仅凭本仓库（本文件 + 源码 + git 历史）即可完整恢复任务背景、已确认结论、已排除的死路、构建/分发方式，并继续推进。
> 阅读顺序：本文档 → `README.md` → `app/src/main` 源码 → git log。
> 标注约定：【确认】= 有实测/源码/遥测直接支撑；【推测】= 合理推断但尚未被直接证实。

---

## 1. 项目是什么

在一台老旧的 Amlogic T968 安卓电视盒子上，用 **GeckoView 144** 自建一个浏览器壳应用「云·原神」，启动即加载米哈游云·原神网页 `https://ys.mihoyo.com/cloud/`，用遥控器 D-pad / 外接键盘操作，从而在电视上玩云·原神。

- 应用包名：`com.cloudgenshin.tv`
- 网页是电脑端云游戏：排队进入后，画面以低时延视频流（H.264）下发，音频伴随。
- 另有一个轻量**安装器**（`installer` 模块），从 GitHub RAW 下载并安装主 APK。

**核心未解决问题（贯穿全程）**：排队进入游戏后，**全屏游戏画面与音频都正常存在，但画面极暗**（需侧角度肉眼辨认）；左上角有一个会随帧/声音跳动的**白色小横条**。**应用重启或闪退的瞬间（约 1 秒），全屏画面恢复正常亮度**，随后进程/页面重建又要重新排队。目标：在**不重启、不重新排队**的前提下，让全屏画面以正常亮度持续运行。

---

## 2. 设备事实（来自应用内诊断 + 设备信息 HTML）

| 项 | 值 |
| :-- | :-- |
| manufacturer / brand | amlogic / Android |
| model / product / device | “AOSP on p331” / p331 / p331 |
| board / hardware | Amlogic-T968 / amlogic |
| Android | 5.1.1，SDK(API) 22，kernel 3.14.29 |
| build type / tags | userdebug / dev-keys |
| ABI | armeabi-v7a, armeabi（32 位） |
| 显示 | 1920×1080，density 1.5 / densityDpi 240，60 Hz，rotation 0 |
| 内存 | total 1228 MB；available 约 219–297 MB；threshold 219；low_memory false |
| GPU | ARM Mali-T830，OpenGL ES 3.1，版本 v1.r7p0-02rel0.9a2b627d8986d580e85bfed4bda9e2d8 |
| 关键 GL 扩展 | `GL_OES_EGL_image_external` = true；`GL_OES_EGL_image_external_essl3` = false |
| H.264 硬解 | `OMX.amlogic.avc.decoder.awesome`（hevc/mpeg4/h263/mpeg2/vc1/wmv3 同为 `OMX.amlogic.*.decoder.awesome`） |

与媒体相关的关键系统属性（诊断中可见）：
- `media.amsuperplayer.defplayer = PV_PLAYER`
- `media.decoder.vfm.defmap = decoder ppmgr deinterlace amvideo`
- `media.amsuperplayer.m4aplayer = STAGEFRIGHT3_PLAYER`
- 多处出现 `amvideo`（Amlogic 专用硬件视频输出设备/平面）。

**日志被系统级静默【确认】**：`persist.sys.logd.level = S`（Silent）。main / system / crash 三个 ring buffer 全程 `0b consumed`；`logcat -d` 返回 `[no output, exit 0]`；live 文件 0 字节。因此**无法依赖 logcat 获取系统/Gecko 日志**。

屏幕本身有老化（红/蓝垂直色带、整体偏色），属**硬件问题，不在软件修复范围**；看日志/截图时只关注信息内容，忽略背景色。

---

## 3. 仓库结构与关键路径

项目根：`cloud-genshin-tv/`

- `app/src/main/java/com/cloudgenshin/tv/`
  - `MainActivity.java` — GeckoRuntime/GeckoView/会话创建、工具菜单 Host 实现、`restartApp`、原生透明处理。
  - `DisplayFix.java` — 内置 WebExtension 的注册与消息通道；亮度档位、画面缩放、重挂流、替换元素、底层画面露出；会话级消息委托。
  - `EnginePrefs.java` — 写出 `engine_config.yaml`（**仅 prefs，不含 env**）。
  - `EngineMode.java` / `DecoderMode.java` — 渲染后端（Texture/Surface）、图形模式、硬解开关。
  - `EnvTool.java` — 尝试反射改 `MOZ_LOG`（在 Android 5.1 失败，best-effort）。
  - `AppLog.java` / `DeviceInfo.java` / `Diag.java` / `LogServer.java` — 应用内日志采集、设备/诊断报告、遥测存储、局域网日志 HTTP 服务。
  - `VirtualMouse.java` 等 — 虚拟鼠标（本阶段无需调整）。
- `app/src/main/assets/displayfix/` — 内置扩展：`manifest.json`、`background.js`、`content.js`。
- `installer/` — 安装器模块。
- `dist/` — 构建产物：`cloud-genshin.apk`（主程序，约 84.7 MB）、`cloud-genshin-installer.apk`（约 133 KB）。
- `build_release.py` — 一键构建脚本。

仓库外的研究资料（在会话工作区，**不在本仓库内**，仅供参考）：
- `../research/` — GeckoView 144 的反编译 `.cpp/.java`、`StaticPrefList.yaml`、`geckoview-144.aar` 等。
- `../android-sdk/`、`../jdk-17/`。

---

## 4. 构建、签名、分发

### 构建
在项目根执行：
```
python3 build_release.py
```
产物：`dist/cloud-genshin.apk` 与 `dist/cloud-genshin-installer.apk`。

### 签名验证（交付前必做）
```
../android-sdk/build-tools/35.0.0/apksigner verify dist/cloud-genshin.apk
```
（apksigner 输出的 META-INF warning 可忽略，结果为验证通过。）

### 分发机制（重要）
- 安装器从**固定地址**下载主 APK：
  `https://raw.githubusercontent.com/Columbine-Hyposelenia/cloud-genshin-tv/master/dist/cloud-genshin.apk`
- 备用镜像前缀（依次拼接该 RAW）：`https://cors.isteed.cc/`、`https://ghfast.top/`、`https://gh-proxy.com/`、`https://ghproxy.net/`、`https://gh.ddlc.cc/`。
- 因为安装器只认 **master 上的 `dist/cloud-genshin.apk`**，每轮都必须把新 APK 提交并推送到 master 该路径；用户用**旧安装器**即可下到最新版。
- **用户长期沿用旧安装器，无需再单独交付安装器。**

### 推送（单次，勿反复试错）
GitHub 服务异常时不要反复重试。使用 PAT 单次推送：
```
git -c http.extraheader="Authorization: Basic $(printf 'x-access-token:%s' <TOKEN> | base64 -w0)" push origin master
```
（令牌由用户在会话中提供，不写入本文件；如需查找见会话记录。）

---

## 5. 工具菜单（ToolsMenu）完整条目（自上而下）

菜单整体包在 `ScrollView` 中，**可上下滚动**（标题提示 dpad up/down to scroll, back to close）。

1. 标题：**工具**
2. 分区 **画面渲染（网页阶段，切换后重启）**
   - 渲染后端：TextureView(默认) / SurfaceView —— 循环，切换后重启
   - 图形模式：硬件 WebRender(默认) 等 —— 循环，切换后重启
3. 分区 **画面（游戏中即时生效）**
   - 亮度补偿：关闭 / 1–5 档 —— 循环，即时
   - 画面缩放：关闭 / ×1.5 … ×16 —— 循环，即时
   - 重挂视频流 —— 动作，即时
   - 替换视频元素 —— 动作，即时
   - 底层画面：遮蔽 / 透明（瞬现）/ 视频层移出 / 视频缩角落 / 视频隐藏（5 档循环，详见 7.3），即时
   - 游戏模式：关 / 开（详见第 9 节），即时
   - 全局快捷键：F1 打开本菜单，F2 切换游戏模式
4. 分区 **解码（切换后重启）**
   - 硬件解码：开启 / 关闭 —— 循环，切换后重启
5. 分区 **诊断与日志**
   - 诊断报告 —— 动作（全屏等宽文本）
   - 日志服务：关闭 / LAN 地址 —— 循环
   - 详细日志：关闭 / 开启（verbose）—— 循环，切换后重启
   - 导出诊断与日志 —— 动作
   - 清空日志 —— 动作
6. 分区 **操作**
   - 重新加载页面
   - 清理缓存并重新加载
   - 重启应用
   - 退出应用

诊断报告（全屏）自上而下包含：SELF TEST、APPLICATION、DEVICE、DISPLAY、MEMORY、GPU/GL、MEDIA CODECS、EFFECTIVE SETTINGS、CONTENT TELEMETRY、GECKO LOG FILE、LOG BUFFER、SYSTEM PROPERTIES (filtered)。

---

## 6. 扩展通道排查史（这些“做不通”的环节都已打通）

内置扩展常量：
- `EXT_ID = displayfix@cloudgenshin.tv`
- `NATIVE_APP = "displayfix"`
- `EXT_URI = resource://android/assets/displayfix/`
- manifest 权限：`tabs`、`nativeMessaging`、`nativeMessagingFromContent`、`geckoViewAddons`、`<all_urls>`；background `background.js`；content_scripts 注入 `content.js`（matches 含 `ys.mihoyo.com`、`*.mihoyo.com`、`*.mihyocdn.com`、`<all_urls>`，all_frames，run_at document_start）。

逐层打通的真实根因：
1. 最初 `content.js` 用 `browser.runtime.connectNative` 连不上、无遥测；补齐 manifest 权限后仍 FAIL。
2. **时序问题**：`ensureBuiltIn` 异步解析常晚于页面首帧；新启用扩展的内容脚本不回溯注入。改为扩展就绪回调之后才 open/loadUri（`DisplayFix.start(onReady)`）。
3. 让 `background.js` 持有 native 端口 —— 此后 `extension port connected` 稳定 PASS。
4. **官方文档关键点**：内容脚本消息必须注册**会话级**委托 `session.getWebExtensionController().setMessageDelegate(extension, delegate, nativeApp)`；扩展级 `extension.setMessageDelegate` 只对应后台。已在 `DisplayFix.bindToSession` 补注册。
5. 为彻底摆脱“manifest 内容脚本自动注入”的不稳定，`background.js` 改用 `browser.tabs.executeScript`（`allFrames`、`matchAboutBlank`、`code` 注入自包含函数）主动执行 `pageApply` 与 `pageTelemetry`，定时 + `tabs.onUpdated` 重注入。此后 `content telemetry`、`media elements found` 均 PASS。

---

## 7. 画面问题：已确认机制（重点）

### 7.1 客观遥测数据（单个 `<video>`）

- CSS 尺寸 1280×720（视口即 1280×720）；解码 `videoWidth 1920 / videoHeight 1080`。
- `readyState 4`，`paused false`，游戏中 `muted true`（音频走另一路径，所以仍有声）。
- `fullscreen null`（**不是** Fullscreen API，内联播放）。
- **2D `drawImage` 读帧**：`luma {mean:0, max:0}`（黑）。
- **WebGL `texImage2D` 读回**：`glLuma {mean:21, max:21}`（极暗但非零）。
- DOM 中**只枚举出一个 `<video>`**。

### 7.2 层级关系（用户实测 + 平台源码 + 文献三方吻合）

1. **真正的全屏游戏画面从头到尾都在**，全屏、有声、内容完全正常，仅极暗；侧角度肉眼可见。它**不会**随对 `<video>` 的 CSS 缩放而移动/缩放。
2. 左上角**横条是独立的上层产物**，能被亮度/缩放/重挂/替换作用；网页自身元素（如右侧“省流中”角标）**不随横条缩放**，说明 transform 只作用于该特定对象。
3. 长时间无操作游戏自行终止后，横条停止跳动，但**仍能被缩放**。
4. 重挂流 / 替换元素会让横条消失后又显现，但**不影响下层全屏暗画面**。
5. 横条放大 ×16 后，呈现为屏幕**顶部一条横向带**（其中能看到云等顶部画面切片），其下方仍是不动的全屏暗画面。

**结论【确认，证据链闭合】：Amlogic 采用“多平面”显示结构。**
- 硬件解码器（`OMX.amlogic.*`）把**完整帧送到 Amlogic 专用硬件视频平面（`amvideo` 平面）**，该平面全屏显示——即用户看到的**不动的全屏（暗）画面**。
- Gecko 走的是 GPU 外部纹理（`SurfaceTexture`，`GL_TEXTURE_EXTERNAL_OES`）路径，但在这套 Mali-T830/老驱动上，外部纹理**只拿到每帧顶部的一条切片**（即“横条”），其余采样为黑。反编译源码 `RenderAndroidSurfaceTextureHost.cpp` 中明确写道：对“MediaCodec 在某些设备输出的变换矩阵不正确”，Gecko 会用 transform override 修正——本机不在其已知设备清单内，故未被修正。
- Gecko 所在的 **OSD/GUI 平面（含横条 + 其余黑色页面）覆盖在硬件视频平面之上**，把全屏真画面压暗。
- **重启 / 闪退时，GUI 平面（覆盖层）先被移除，而硬件视频平面尚未关闭，于是约 1 秒内全屏画面以正常亮度显现**；随后视频平面也被销毁/页面重载。这解释了“关闭/闪退/关机前一瞬间画面恢复”。

文献佐证：
- AOSP《Android TV 图形架构》：SurfaceView 提供独立合成层、SurfaceFlinger 作为硬件叠加层合成；TextureView 由 GPU 在应用窗口内合成（不产生独立层、不打孔）。
- Kodi / Amlogic 社区：视频由硬件渲染到“与 GUI 无关的另一视频平面”；启用 Amlogic 硬解时常见“只有声音、黑屏/画面异常，关闭瞬间正常”。
- 同一问题在**安装 Gecko 之前**用其他方式也出现 —— 证明根因在 **Amlogic 平台视频输出/平面合成**，与具体浏览器引擎无关；Gecko 只是我们自带的内核。

### 7.3 修复方向（在线移除覆盖层，而非把横条变成画面）

> 关键认知：**不要试图把横条放大成画面**——下层已有完整全屏画面，那样会造成两个视频叠加且几何破坏。正确方向是**让覆盖在硬件视频平面上的 GUI 内容透明/移除**，露出底层全屏真画面。

**最新突破【确认】**：切换“底层画面”时，无论切向“露出”还是切回“遮蔽”，**都只在切换的一瞬间（窗口/合成表面重建的过渡帧）闪现正常全屏画面，表面稳定后覆盖层立即重新建立**。手动快速连点该键，理论上可让画面持续显现。这证明：变亮是 surface/format relayout 的**瞬态**，必须找到一个**稳定**不覆盖硬件平面的配置。未进游戏时点该键网页也会闪（无害，测试时避免在网页阶段操作）。

**“底层画面”已升级为 5 档循环（菜单“画面”区，即时、可回退）**，用于一次逐档找出稳定露出方式：

| 档 | 标签 | 页面动作 | 目的 / 机制 |
| :-- | :-- | :-- | :-- |
| 0 | 遮蔽 | 全部还原 | 正常不透明状态（对照） |
| 1 | 透明（瞬现） | `<video>` opacity:0、页面背景全透明、窗口 OPAQUE→TRANSLUCENT | 窗口半透明；实测仅过渡帧显现，稳定态被覆盖 |
| 2 | 视频层移出 | 视频 `position:fixed`、`transform:translateX(-1500px)` 移出屏幕、页面透明、窗口半透明 | 用**几何位移**把覆盖 quad 移到屏外（解码/缓冲尺寸不变，amvideo 平面应保持全屏）；几何移除可能比 opacity 更可靠 |
| 3 | 视频缩角落 | 视频 `position:fixed`、宽高 2px、页面透明、窗口半透明 | 让覆盖 quad 缩成角落小点，屏内其余区域露出；注意可能改变 SurfaceTexture 缓冲尺寸 |
| 4 | 视频隐藏 | 视频 `display:none`、页面透明、窗口半透明 | 彻底移除元素；**可能冻结/关闭解码器与 amvideo 平面**，作为对照验证 |

非 0 档都会同时：原生 View 透明、窗口背景透明、窗口格式 TRANSLUCENT。回 0 档全部还原（opacity/position/left/top/margin/transform/width/height/display 逐个清空）。

**二次收窄【确认】**：实测档 2/3/4（纯页面 CSS：移出/缩角/隐藏）**不产生全屏显现**；只有 0↔1 切换（即原生 `Window.setFormat` OPAQUE↔TRANSLUCENT 触发的**窗口表面重建**）才在过渡帧显现。结论：变亮由**窗口表面 relayout 的空档**产生，页面 CSS 改动无法稳定移除覆盖。

**稳定打孔（本轮新增，`HoleSurfaceView`）**：为把“一瞬的空档”变成“持续的透明”，新增一个全屏、`PixelFormat.TRANSLUCENT` 的 SurfaceView（`HoleSurfaceView.java`），在任意非 0 档 `setVisibility(VISIBLE)`。它通过 `gatherTransparentRegion` 在宿主窗口上**持续**打一个全屏透明孔（SurfaceFlinger 每帧维护，而非 relayout 那一帧），自身表面用 `lockCanvas` + CLEAR 清成全透明且不绘任何不透明内容，使窗口后方的 amvideo 硬件平面透过该孔持续显示。回 0 档 GONE，孔关闭。这是与“仅 relayout 瞬现”相对的标准机制，为本轮首要验证项。

**关于“调换层级 / 让游戏画面盖住遮罩”（用户设想）的可行性分析**：Android 中对 SurfaceView 表面可用 `setZOrderOnTop(true)`（置于宿主窗口之上）或 `setZOrderMediaOverlay(true)`（置于其它 SurfaceView 之上、窗口之下）调整 z 序。若解码器输出挂在我们持有的 SurfaceView 上，可把它抬到 GUI 之上实现“画面盖住遮罩”。但本机 Gecko 用 SurfaceTexture 承载解码器输出，amvideo 平面由 HAL/解码器管理，普通应用无法直接对其调 z 序，故该设想**当前不可直接实施**；若后续把解码输出改挂到自有 SurfaceView，则 `setZOrderOnTop` 是一条等价于“移除遮罩”的可行路线，记录备查。

**关于“SurfaceView 后端不渲染”**：GeckoView 的 BACKEND_SURFACE 是把**整个 Gecko 合成页面**放进一个 SurfaceView 表面，解码器仍输出到 Gecko 内部 SurfaceTexture，因此它并不会为 amvideo 平面自动打孔；此前“本机不渲染”可能是该后端在本机 EGL/驱动上真的失败，也可能是“渲染了但被平面/孔遮挡”。鉴于 HoleSurfaceView 已独立实现打孔，无需依赖切换 GeckoView 后端即可验证。

【已确认修复】进入游戏极暗状态后切到**档 1**，窗口 relayout 闪光后，HoleSurfaceView 的全屏透明孔让 amvideo 硬件平面**持续以正常亮度显示**，画面不再回到极暗；声音、操控、网络会话均正常。**画面问题已解决。** 标准操作：进入游戏后把“底层画面”切到档 1（透明/瞬现）。后续可在检测到视频播放后自动应用档 1。

**已知小问题（美观，不影响点击）**：露出状态下虚拟鼠标指针在移动幅度大/长按时会残缺甚至“消失”，但点击仍有效；未露出（网页）时正常。原因：HoleSurfaceView 的全屏 gatherTransparentRegion 把该区域内**窗口自身绘制内容（含虚拟鼠标 overlay View）一并置透明**，指针落在孔区域被打孔。可选修复：把虚拟鼠标改为 `setZOrderOnTop(true)` 的独立 SurfaceView 绘制。暂不处理。

<details><summary>此前验证预案（存档）</summary>

- 若闪光后仍回到极暗：说明 amvideo 平面与应用 SurfaceView 表面的 z 序不满足透出条件，下一步尝试对 HoleSurfaceView `setZOrderMediaOverlay` / 调整其表面格式，或把解码输出改挂自有 SurfaceView 并 `setZOrderOnTop`。
- 其他可考虑的在线动作：在线切换 `media.hardware-video-decoding.enabled`（Gecko 中为 `RelaxedAtomicBool`，可实时生效），迫使解码器/sink 重建而不重协商 WebRTC、不重载页面。
</details>

---

## 8. 已验证“做不通 / 勿重复”的方向

- **logcat 路径不可用**：`persist.sys.logd.level=S`，所有 ring buffer 0 字节。
- **engine_config.yaml 的 `env` 不被 GeckoView 解析**：反编译 `GeckoThread.class` 字符串只含 `args`、`prefs`，不含 `env`（automation 文档里的 env 由 geckodriver 处理）。故经该途径注入 `MOZ_LOG/MOZ_LOG_FILE` 无效，`gecko.log` 始终 0 字节。
- **EnvTool 反射改 `MOZ_LOG` 在 Android 5.1 失败**（含 `ProcessEnvironment$StringEnvironment` 内部类构造器兜底）。
- **移除 GeckoView（removeView/resetDisplay）**会导致卡顿、无法操控，随后闪退，不可用。
- **CSS brightness / transform 只明显作用于横条**，不能恢复全屏画面。
- **IjkPlayer 只能吃 URL/HLS**，无法接 WebRTC 实时流，不能用于直播画面。
- **GeckoView SurfaceView 后端此前本机不渲染**，故一直用 TextureView（但见 7.3，需在硬件平面视角下重新评估）。
- 键盘/遥控器/虚拟鼠标功能正常，**本阶段不要调整**；对当前未产生影响的部分不做投机改动。

---

## 9. 输入与按键（游戏模式）

### 9.1 原神 PC 端默认按键（多来源核实）

| 操作 | 默认按键 |
| :-- | :-- |
| 移动 | W / A / S / D |
| 普攻 | 鼠标左键（**长按 = 重击/蓄力攻击**） |
| 元素战技 | E（部分角色长按蓄力） |
| 元素爆发（大招） | Q |
| 冲刺/闪避 | 左 Shift **或** 鼠标右键 |
| 跳跃 / 风之翼 | Space |
| 拾取 / 交互 | F |
| 弓箭瞄准 | R |
| 切换队伍角色 | 1 / 2 / 3 / 4（Alt+数字 切换并放大招） |
| 元素视野 | 长按鼠标中键 |
| 显示鼠标指针 | 按住 Alt |
| 行走/奔跑切换 | 左 Ctrl |
| 快速下落 / 取消攀爬 | X |
| 地图 / 背包 / 角色 / 任务 | M / B（一说 I）/ C（一说 K）/ J |
| 派蒙菜单 | Esc |

### 9.2 旧输入方案的问题

- 物理键盘设备一旦发送字母/数字即被识别，其按键**直通页面**（WASD/E/Q/Space/F/Shift 等本就能用）。
- 遥控器 D-pad 原先驱动一个**绝对指针**，并向 GeckoView 注入 **SOURCE_TOUCHSCREEN 触摸事件**：在桌面 UA 的云游戏里触摸只被偶尔兼容成点击（故确认键偶发普攻、随后识别不到），且**没有真正的鼠标左右键、没有相对位移**，无法稳定普攻/重击/冲刺，也无法转动视角。

### 9.3 游戏模式（本轮新增）

**机制**：`VirtualMouse` 新增向 GeckoView 注入**真正的鼠标事件**（`InputDevice.SOURCE_MOUSE`）：
- 用带 `buttonState` 的 `MotionEvent.obtain(...)` 重载（API 14+，minSdk21 满足；`setButtonState` 非公开故不用）。
- 无按键移动 → `ACTION_HOVER_MOVE` 经 `dispatchGenericMotionEvent` 触发 `mousemove`。
- 左键 → `ACTION_DOWN/UP` + `BUTTON_PRIMARY` 经 `dispatchTouchEvent`；右键 → `BUTTON_SECONDARY`。

**进入/退出**：
- 键盘 **F1 = 打开工具菜单**；**F2 = 切换游戏模式**（遥控器可用菜单内“游戏模式”项）。
- 游戏模式 ON（相对模式 `setRelative(true)`）：
  - **方向键/方向舵 = 相对转视角**（每帧固定 LOOK_SPEED 增量，指针在屏幕内回绕），隐藏指针图标。
  - **L 键 / 遥控器确认 = 鼠标左键**：按下即 DOWN、松开即 UP（真按住），点按=普攻、长按=重击。
  - **遥控器返回 = 鼠标右键（冲刺）**（游戏模式下）；键盘冲刺直接用原生 Shift。
  - WASD/E/Q/Space/F/R/1-4 等继续直通游戏。
- 游戏模式 OFF：恢复绝对指针与触摸导航；遥控器返回恢复为“后退”。

### 9.4 待设备验证 / 后续

- 验证游戏模式下：方向键转视角是否平滑、L/确认能否稳定普攻与长按重击、Shift/返回冲刺、其余技能键是否正常。
- 若云游戏需要 **Pointer Lock** 才转视角：后续在页面侧请求 `requestPointerLock` 或由扩展在进入游戏模式时对画布/视频调用。
- 指针图标在“露出（打孔）”状态下被透明孔裁掉（见 7.3），属美观问题；可改为 `setZOrderOnTop` 的独立 SurfaceView 绘制指针，同时可一并解决本模式指针。
- 按键映射后续可做成用户可自定义；当前为固定默认。

---

## 10. 工作方式与用户约束

- 中文回复；不用 emoji。
- 谨慎、严格基于代码与实际信息；不做未产生影响的投机改动；结论结合实测，不臆造。
- 深度调研，无时间限制，但**最终必须修复画面**。
- 截图只需关键段落（SELF TEST、CONTENT TELEMETRY）；日志信息收集应在点击收集时实时开始，以便与游戏内画面对齐。
- 每轮把新 APK 提交推送到 master 的 `dist/cloud-genshin.apk`；安装器不再单独交付。
- 新代码**不写中文注释**（历史代码中的旧注释不管）。
- 不要在 GitHub 上耗时；服务出错时按用户提供的凭证单次尝试，不反复试错恢复。

---

## 11. 参考资料（文献）

- AOSP, Android TV 图形架构（TextureView / SurfaceView / 硬件叠加层）：https://source.android.google.cn/docs/core/graphics/arch-tv
- AOSP, Multimedia tunneling（HWC 合成 tunneled 视频）：https://source.android.google.cn/docs/devices/tv/multimedia-tunneling
- AOSP, Implement Hardware Composer HAL：https://source.android.google.cn/docs/core/graphics/implement-hwc
- Kodi 论坛, AMLogic hardware video decoding（视频在独立视频平面、有声黑屏）：https://forum.kodi.tv/showthread.php?pid=1654729
- Kodi 论坛, Android Hardware acceleration on Android SmartTV：https://forum.kodi.tv/showthread.php?tid=272412
- dev.to, Cinebox：SurfaceView 透明区与 gatherTransparentRegion：https://dev.to/serbyte/integrating-mpv-video-player-into-rust-based-cinebox-gui-for-cross-platform-4k-hdr-support-ic6
- 博客园, 电视硬解走独立硬件通道 / HWC：https://www.cnblogs.com/ckds/articles/19978976
- Linux Kernel, Amlogic Meson VPU（多平面：VDIN/VIU/视频后处理）：https://www.kernel.org/doc/html/v6.14-rc1/_sources/gpu/meson.rst.txt
