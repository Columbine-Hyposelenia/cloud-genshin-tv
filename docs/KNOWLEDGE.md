# 云·原神 TV — 知识库与上下文手册

> 用途：即使对话上下文完全丢失，仅凭本仓库（本文件 + 源码 + git 历史）即可完整恢复任务背景、当前可用状态、历史排查经验、构建/分发方式，并继续推进。
> 阅读顺序：本文档 → `README.md` → `app/src/main` 源码 → git log。
> 标注约定：
> - 【当前】= 现行、稳定的实现与状态；
> - 【历史】= 排查过程中出现过的现象/推断，作为经验保留，问题均已解决；
> - 【确认】= 有实测、源码或遥测直接支撑。

---

## 1. 项目概述与当前状态

在一台老旧的 Amlogic T968 安卓电视盒子上，用 **GeckoView 144** 自建浏览器壳应用「云·原神」（包名 `com.cloudgenshin.tv`），启动即加载米哈游云·原神网页 `https://ys.mihoyo.com/cloud/`，用遥控器 D-pad / 外接键盘操作，在电视上玩云·原神。网页为电脑端云游戏，排队进入后画面以低时延 H.264 视频流下发、音频伴随。

### 1.1 当前状态总览（结论先行）

| 事项 | 状态 | 说明 |
| :-- | :-- | :-- |
| 进入游戏后画面极暗 | **已解决** | 全屏透明孔透出硬件视频平面，见第 6 节 |
| 左上角跳动白色小横条 | **已不再影响观看** | 属外部纹理切片，露出后真画面在其下层持续显示 |
| 游戏输入（鼠标/视角/攻击/冲刺） | **已实现，可用** | 游戏模式，见第 7 节 |
| 游戏模式切换居中弹窗 | **已移除** | 不再常驻挡屏，状态仅在菜单行显示（见 7.2） |
| 键盘/遥控器/虚拟鼠标基础操作 | **正常** | 无需调整 |
| 已知遗留 | 仅 1 项美观问题 | 露出状态下虚拟鼠标指针被透明孔裁切，不影响点击，见 6.5 |

### 1.2 标准使用流程（当前）

1. 打开应用，自动加载网页并排队。
2. 排队进入游戏后，打开工具菜单，把“底层画面”切到 **档 1（透明）**：全屏画面立即以正常亮度持续显示，声音、操控、网络会话均不中断，无需重启或重新排队。
3. 需要游戏操作时，用菜单或键盘 F2 打开“游戏模式”（见第 7 节）。

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

与媒体相关的关键系统属性：
- `media.amsuperplayer.defplayer = PV_PLAYER`
- `media.decoder.vfm.defmap = decoder ppmgr deinterlace amvideo`
- `media.amsuperplayer.m4aplayer = STAGEFRIGHT3_PLAYER`
- 多处出现 `amvideo`（Amlogic 专用硬件视频输出设备/平面）。

**系统日志被静默【确认】**：`persist.sys.logd.level = S`（Silent）。main / system / crash 三个 ring buffer 全程 `0b consumed`；`logcat -d` 返回 `[no output, exit 0]`；live 文件 0 字节。因此无法依赖 logcat 获取系统/Gecko 日志，应用改用内置诊断与局域网日志服务采集信息。

屏幕本身有老化（红/蓝垂直色带、整体偏色），属硬件问题，不在软件修复范围；看截图时只关注信息内容，忽略背景色。

---

## 3. 仓库结构与关键路径

项目根：`cloud-genshin-tv/`

- `app/src/main/java/com/cloudgenshin/tv/`
  - `MainActivity.java` — GeckoRuntime/GeckoView/会话创建、工具菜单 Host 实现、`restartApp`、原生透明处理。
  - `DisplayFix.java` — 内置 WebExtension 的注册与消息通道；亮度档位、画面缩放、重挂流、替换元素、底层画面多档露出；会话级消息委托。
  - `HoleSurfaceView.java` — 全屏透明打孔 SurfaceView，画面修复的核心组件。
  - `EnginePrefs.java` — 写出 `engine_config.yaml`（仅 prefs，不含 env）。
  - `EngineMode.java` / `DecoderMode.java` — 渲染后端（Texture/Surface）、图形模式、硬解开关。
  - `EnvTool.java` — 尝试反射改 `MOZ_LOG`（在 Android 5.1 失败，best-effort）。
  - `AppLog.java` / `DeviceInfo.java` / `Diag.java` / `LogServer.java` — 应用内日志采集、设备/诊断报告、遥测存储、局域网日志 HTTP 服务。
  - `VirtualMouse.java` — 虚拟鼠标与游戏模式输入。
  - `KeyboardRouter.java` — 统一键盘/遥控按键路由、输入设备识别、游戏模式切换（F1/F2）、攻击/冲刺/视角回调。
  - `ToolsMenu.java` — 遥控器 D-pad 工具菜单 Dialog（包在 ScrollView 中可滚动）。
  - `ReportScreen.java` / `GpuInfo.java` — 全屏诊断报告展示、独立 EGL 上下文采集 GPU 信息。
- `app/src/main/assets/displayfix/` — 内置扩展：`manifest.json`、`background.js`、`content.js`。
- `installer/` — 安装器模块。
- `dist/` — 构建产物：`cloud-genshin.apk`（主程序，约 84.7 MB）、`cloud-genshin-installer.apk`（约 133 KB）。
- `build_release.py` — 一键构建脚本。

仓库外的研究资料（在会话工作区，不在本仓库内，仅供参考）：
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

### 分发机制

- 安装器从以下地址下载主 APK（`RAW` 即该地址，其余通道为其镜像前缀拼接）：
  `https://raw.githubusercontent.com/Columbine-Hyposelenia/cloud-genshin-tv/master/dist/cloud-genshin.apk`
- 【当前】通道列表（`installer/.../MainActivity.java` 的 `MIRRORS`，顺序仅作兜底，实际由板端测速择优）：
  1. `https://gh-proxy.com/` + RAW
  2. `https://ghfast.top/` + RAW
  3. `https://cdn.gh-proxy.com/` + RAW
  4. `https://ghproxy.net/` + RAW
  5. `https://cors.isteed.cc/` + RAW
  6. `https://gh.ddlc.top/` + RAW
  7. RAW（官方源兜底）
- 【当前】下载策略（`ApkDownloader.java`）：
  - **板端测速择优**：并发对每个通道请求 `Range: bytes=0-524287`（512 KB，8 s 上限），校验 HTTP 200/206 且首字节为 `0x50`（APK 的 `PK` 魔法），按实测速率降序排列；失败通道排到最后仍会兜底尝试。状态栏依次显示 `measuring mirror speed...` 与 `speed 6:2316kB/s | 1:629kB/s ...`。
  - **多段并发下载**：对首选通道先 `Range: bytes=0-0` 取总长（需 206 且 > 8 MB），再用 4 个线程分段（各自 `Range` + 独立 `FileChannel.write(buffer, position)` 绝对定位写入同一文件，不占额外空间），进度按累计字节汇总；任一段失败或服务器不支持 206/`Content-Range` 不匹配即整段回退为单连接下载（原逻辑）。
  - **完整性校验**：大小与 `Content-Length`/总长一致、首 4 字节为 `PK\x03\x04`，否则判为无效并切换下一通道。
- 【实测·2026-11】用真实 `ApkDownloader.java`（配 Android 桩类，JDK17）对线上产物完整下载验证：84,730,989 字节、耗时约 18.7 s、均速约 4.4 MB/s（单连接探测为 2.3 MB/s，4 段并发约 1.9 倍）；下载结果与远端 master 的 blob **逐字节一致**（SHA256 `25b4902...`），apksigner 校验通过、`unzip -t` 无损。
- 安装器只认 master 上的 `dist/cloud-genshin.apk`；主程序更新时把新 APK 提交推送到 master 该路径即可，用户沿用旧安装器即可下到最新版。
- 安装器自身更新需手动安装 `dist/cloud-genshin-installer.apk`（无自更新通道）。

### 推送（单次，勿反复试错）

GitHub 服务异常时不要反复重试。使用 PAT 单次推送：

```
git -c http.extraheader="Authorization: Basic $(printf 'x-access-token:%s' <TOKEN> | base64 -w0)" push origin master
```

（令牌由用户在会话中提供，不写入本文件；如需查找见会话记录。）

---

## 5. 工具菜单（当前完整条目，自上而下）

菜单整体包在 `ScrollView` 中，可上下滚动；按 BACK/MENU/ESCAPE 关闭。（“dpad up/down to scroll, back to close”这句提示实际出现在诊断报告页 `ReportScreen`，不是工具菜单。）

1. 标题：**工具**
2. 分区 **画面渲染（网页阶段，切换后重启）**
   - 渲染后端：TextureView(默认) / SurfaceView —— 循环，切换后重启
   - 图形模式：硬件 WebRender(默认) 等 —— 循环，切换后重启
3. 分区 **画面（游戏中即时生效）**
   - 亮度补偿：关闭 / 1–5 档 —— 循环，即时
   - 画面缩放：关闭 / ×1.5 … ×16 —— 循环，即时
   - 重挂视频流 —— 动作，即时
   - 替换视频元素 —— 动作，即时
   - 底层画面：遮蔽 / 透明 / 视频层移出 / 视频缩角落 / 视频隐藏（5 档循环，当前使用档 1，详见 6.4），即时
   - 游戏模式：关 / 开（详见第 7 节），即时
   - 注：F1/F2 是物理全局快捷键而非菜单行（F1 打开菜单，F2 切换游戏模式）
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

诊断报告（全屏）自上而下包含：SELF TEST、APPLICATION、DEVICE、DISPLAY、MEMORY、GPU/GL、MEDIA CODECS、EFFECTIVE SETTINGS、CONTENT TELEMETRY、LOG BUFFER、SYSTEM PROPERTIES (filtered)。

---

## 6. 画面问题：根因与最终方案

### 6.1 问题现象（历史，已解决）

【历史】排队进入游戏后，全屏游戏画面与音频都正常存在，但画面极暗（需侧角度肉眼辨认），左上角有一个随帧/声音跳动的白色小横条；应用重启或闪退的瞬间（约 1 秒）全屏画面曾短暂恢复正常亮度。该问题现已通过 6.4 的方案稳定解决，无需重启或重新排队。

### 6.2 客观遥测数据（单个 `<video>`，【确认】）

- CSS 尺寸 1280×720（视口即 1280×720）；解码 `videoWidth 1920 / videoHeight 1080`。
- `readyState 4`，`paused false`，游戏中 `muted true`（音频走另一路径，所以仍有声）。
- `fullscreen null`（不是 Fullscreen API，内联播放）。
- 2D `drawImage` 读帧：`luma {mean:0, max:0}`（黑）。
- WebGL `texImage2D` 读回：`glLuma {mean:21, max:21}`（极暗但非零）。
- DOM 中只枚举出一个 `<video>`。

### 6.3 根因：Amlogic 多平面显示结构（【确认】，证据链闭合）

- 硬件解码器（`OMX.amlogic.*`）把**完整帧送到 Amlogic 专用硬件视频平面（`amvideo` 平面）**，该平面全屏显示——即下层那个内容正常、完整、有声的全屏画面。
- Gecko 走 GPU 外部纹理（`SurfaceTexture`，`GL_TEXTURE_EXTERNAL_OES`）路径，但在这套 Mali-T830/老驱动上，外部纹理**只拿到每帧顶部的一条切片**（即左上角横条），其余采样为黑。反编译源码 `RenderAndroidSurfaceTextureHost.cpp` 明确写道：对“MediaCodec 在某些设备输出的变换矩阵不正确”，Gecko 用 transform override 修正——本机不在其已知设备清单内，故未被修正。
- Gecko 所在的 **OSD/GUI 平面（含横条 + 其余黑色页面）覆盖在硬件视频平面之上**，把全屏真画面压暗。
- 重启/闪退时，GUI 平面（覆盖层）先被移除而硬件视频平面尚未关闭，因此约 1 秒内全屏画面以正常亮度显现——这解释了“关闭瞬间画面恢复”的历史现象。
- 同一问题在安装 Gecko 之前用其他方式也出现，证明根因在 Amlogic 平台视频输出/平面合成，与具体浏览器引擎无关。

### 6.4 最终解决方案（【当前】，稳定有效）

思路不是把横条放大成画面（下层已有完整全屏画面，那样会造成两个视频叠加且几何破坏），而是**让覆盖在硬件视频平面上的 GUI 内容持续透明，露出下层真画面**。当前实现由两部分组成：

1. **窗口半透明**：把 `<video>` 的 `opacity` 设为 0（保持解码/播放/音频不中断）、注入样式令 `html,body,body *` 背景全透明，原生 View 与窗口背景透明，并把窗口格式由 `OPAQUE` 切到 `TRANSLUCENT`。
2. **`HoleSurfaceView` 稳定打孔**：一个全屏、`PixelFormat.TRANSLUCENT` 的 SurfaceView，露出时 `setVisibility(VISIBLE)`。它通过 `gatherTransparentRegion` 在宿主窗口上**持续**打一个全屏透明孔（SurfaceFlinger 每帧维护，而非仅在窗口 relayout 的那一帧），自身表面用 `lockCanvas` + CLEAR 清成全透明且不绘任何不透明内容，使窗口后方的 `amvideo` 硬件平面透过该孔持续以正常亮度显示。

“底层画面”5 档循环（即时、可回退）：

| 档 | 标签 | 页面动作 | 备注 |
| :-- | :-- | :-- | :-- |
| 0 | 遮蔽 | 全部还原 | 正常不透明状态（对照） |
| 1 | 透明 | `<video>` opacity:0、页面背景全透明、窗口 TRANSLUCENT、启用 HoleSurfaceView | **当前标准使用档，稳定露出真画面** |
| 2 | 视频层移出 | 视频 `position:fixed`、`transform:translateX(-1500px)` 移出屏幕 | 排查期对照档，见 6.6 |
| 3 | 视频缩角落 | 视频 `position:fixed`、宽高 2px | 排查期对照档 |
| 4 | 视频隐藏 | 视频 `display:none` | 排查期对照档 |

非 0 档都会令原生 View/窗口背景透明、窗口格式 TRANSLUCENT；回 0 档全部还原（opacity/position/left/top/margin/transform/width/height/display 逐个清空），HoleSurfaceView 置 GONE、孔关闭。

### 6.5 已知遗留问题（美观，不影响功能）

露出状态下，虚拟鼠标指针在移动幅度大或长按时会残缺甚至“消失”，但点击仍有效；未露出（网页）时正常。原因：HoleSurfaceView 的全屏 `gatherTransparentRegion` 会把该区域内窗口自身绘制内容（含虚拟鼠标 overlay View）一并置透明，指针落在孔区域被打孔。可选修复：把虚拟鼠标改为 `setZOrderOnTop(true)` 的独立 SurfaceView 绘制。当前暂不处理。

### 6.6 排查历程（历史经验存档）

以下为解决过程中按时间顺序得到的关键认知，均为历史，结论已收敛到 6.4：

1. 早期曾推断横条是“整帧被错误 SurfaceTexture transform/缓冲几何压到角落”，据此加入缩放、重挂流、替换元素；实测这些动作只作用于横条，不影响下层全屏画面。
2. 随后确认：真正的全屏游戏画面从头到尾都在下层（全屏、有声、内容正常、仅极暗），横条是独立上层产物；网页自身元素（如右侧“省流中”角标）不随横条缩放，说明 transform 只作用于该特定对象。
3. 加入窗口半透明开关后发现：两个方向切换都只在切换瞬间（窗口/合成表面重建的过渡帧）闪现正常画面，表面稳定后覆盖层立即重建；快速连点理论上可让画面持续显现。
4. 把“底层画面”升级为 5 档后实测：档 2/3/4 的纯页面 CSS 改动（移出/缩角/隐藏）不产生全屏显现，只有窗口 `setFormat` 触发的窗口表面 relayout 才在过渡帧显现——由此确认变亮是窗口 relayout 空档，纯 CSS 无法稳定移除覆盖。
5. 引入 HoleSurfaceView 用 `gatherTransparentRegion` 打持续透明孔后，`amvideo` 平面得以持续透出，画面问题稳定解决。

<details><summary>曾考虑但未采用的备选/应急方案（存档）</summary>

- 若稳定打孔未成功，曾计划对 HoleSurfaceView 调 `setZOrderMediaOverlay` / 调整表面格式，或把解码输出改挂自有 SurfaceView 并 `setZOrderOnTop`。
- “调换层级、让游戏画面盖住遮罩”的设想对应 SurfaceView 的 `setZOrderOnTop / setZOrderMediaOverlay`；因 Gecko 用 SurfaceTexture 承载解码器、amvideo 平面由 HAL 管理，普通应用无法直接调其层级，故未采用。
- GeckoView 的 BACKEND_SURFACE 是把整个 Gecko 合成页面放进一个 SurfaceView 表面，解码器仍输出到内部 SurfaceTexture，不会为 amvideo 自动打孔；因此画面修复不依赖切换 GeckoView 后端。
- 曾考虑在线切换 `media.hardware-video-decoding.enabled`（Gecko 中为 `RelaxedAtomicBool`，可实时生效），迫使解码器/sink 重建而不重协商 WebRTC、不重载页面。
</details>

---

## 7. 输入与游戏模式（【当前】）

### 7.1 原神 PC 端默认按键（多来源核实）

| 操作 | 默认按键 |
| :-- | :-- |
| 移动 | W / A / S / D |
| 普攻 | 鼠标左键（长按 = 重击/蓄力攻击） |
| 元素战技 | E（部分角色长按蓄力） |
| 元素爆发（大招） | Q |
| 冲刺/闪避 | 左 Shift 或 鼠标右键 |
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

### 7.2 游戏模式机制（当前实现）

`VirtualMouse` 向 GeckoView 注入真正的鼠标事件（`InputDevice.SOURCE_MOUSE`）：
- 用带 `buttonState` 的 `MotionEvent.obtain(...)` 重载（API 14+，minSdk21 满足；`setButtonState` 非公开故不用）。
- 无按键移动 → `ACTION_HOVER_MOVE` 经 `dispatchGenericMotionEvent` 触发 `mousemove`。
- 左键 → `ACTION_DOWN/UP` + `BUTTON_PRIMARY` 经 `dispatchTouchEvent`；右键 → `BUTTON_SECONDARY`。

进入/退出：
- 键盘 F1 打开工具菜单；F2 切换游戏模式（遥控器可用菜单内“游戏模式”项）。
- 游戏模式 ON（相对模式 `setRelative(true)`）：
  - 方向键/方向舵 = 相对转视角（每帧固定 LOOK_SPEED 增量，指针在屏幕内回绕），隐藏指针图标。
  - L 键 / 遥控器确认 = 鼠标左键：按下即 DOWN、松开即 UP（真按住），点按=普攻、长按=重击。
  - 遥控器返回 = 鼠标右键（冲刺）；键盘冲刺直接用原生 Shift。
  - WASD/E/Q/Space/F/R/1-4 等继续直通游戏。
- 游戏模式 OFF：恢复绝对指针与触摸导航；遥控器返回恢复为“后退”。

切换游戏模式不再弹出居中卡片：历史上该卡片使用不自动关闭的 `showStatus`、会常驻挡屏，已在提交 f1762a6 移除（`KeyboardRouter` 保留空实现，切换逻辑不受影响）；当前开/关状态只通过菜单行“游戏模式：…”文字反馈。

### 7.3 旧输入方案的问题（历史）

【历史】物理键盘的字母/数字本就直通页面、可直接使用；遥控器 D-pad 原先驱动绝对指针并注入 `SOURCE_TOUCHSCREEN` 触摸事件，在桌面 UA 云游戏里触摸只被偶尔兼容成点击，且没有真正的鼠标左右键与相对位移，无法稳定普攻/重击/冲刺与转视角。游戏模式（7.2）用真实鼠标事件解决了这些问题。

### 7.4 后续可选项

- 若云游戏需要 Pointer Lock 才转视角：可在页面侧请求 `requestPointerLock`，或由扩展在进入游戏模式时对画布/视频调用。
- 指针图标在露出状态被透明孔裁切（见 6.5），可改为 `setZOrderOnTop` 的独立 SurfaceView 绘制，同时一并优化游戏模式指针。
- 按键映射后续可做成用户可自定义；当前为固定默认。

---

## 8. 扩展架构与通道（当前实现 + 排查经验）

内置扩展常量：
- `EXT_ID = displayfix@cloudgenshin.tv`
- `NATIVE_APP = "displayfix"`
- `EXT_URI = resource://android/assets/displayfix/`
- manifest 权限：`tabs`、`nativeMessaging`、`nativeMessagingFromContent`、`geckoViewAddons`、`<all_urls>`；background `background.js`；content_scripts 注入 `content.js`（matches 含 `ys.mihoyo.com`、`*.mihoyo.com`、`*.mihyocdn.com`、`<all_urls>`，all_frames，run_at document_start）。

【当前】`background.js` 用 `browser.tabs.executeScript`（`allFrames`、`matchAboutBlank`、`code` 注入自包含函数）主动执行 `pageApply`（亮度/几何/露出）与 `pageTelemetry`（采集媒体信息），定时 + `tabs.onUpdated` 重注入，通道稳定。

排查期打通的关键经验（历史）：
1. 最初 `content.js` 用 `browser.runtime.connectNative` 连不上、无遥测；补齐 manifest 权限后仍 FAIL。
2. 根因是时序：`ensureBuiltIn` 异步解析常晚于页面首帧，新启用扩展的内容脚本不回溯注入；改为扩展就绪回调之后才 open/loadUri（`DisplayFix.start(onReady)`）。
3. 让 `background.js` 持有 native 端口后，`extension port connected` 稳定 PASS。
4. 按官方文档，内容脚本消息必须注册**会话级**委托 `session.getWebExtensionController().setMessageDelegate(extension, delegate, nativeApp)`；扩展级 `setMessageDelegate` 只对应后台。已在 `DisplayFix.bindToSession` 补注册。
5. 为彻底摆脱 manifest 自动注入的不稳定，最终改为上述主动 `executeScript` 方案。

---

## 9. 已验证的死路（勿重复）

- **logcat 不可用**：`persist.sys.logd.level=S`，所有 ring buffer 0 字节。
- **engine_config.yaml 的 `env` 不被 GeckoView 解析**：反编译 `GeckoThread.class` 字符串只含 `args`、`prefs`，不含 `env`（automation 文档里的 env 由 geckodriver 处理）。经该途径注入 `MOZ_LOG/MOZ_LOG_FILE` 无效，`gecko.log` 始终 0 字节。
- **EnvTool 反射改 `MOZ_LOG` 在 Android 5.1 失败**（含 `ProcessEnvironment$StringEnvironment` 内部类构造器兜底）。
- **移除 GeckoView（removeView/resetDisplay）**会导致卡顿、无法操控，随后闪退，不可用。
- **CSS brightness / transform 只作用于横条**，不能恢复全屏画面。
- **IjkPlayer 只能吃 URL/HLS**，无法接 WebRTC 实时流，不能用于直播画面。
- **GeckoView SurfaceView 后端在本机不渲染**，故一直用 TextureView（其局限见 6.6 存档说明）。
- 键盘/遥控器/虚拟鼠标基础功能正常，不做未产生影响的投机改动。

---

## 10. 工作方式与用户约束

- 中文回复；不用 emoji。
- 谨慎、严格基于代码与实际信息；结论结合实测，不臆造。
- 截图只需关键段落（SELF TEST、CONTENT TELEMETRY）；日志信息收集应在点击收集时实时开始，以便与游戏内画面对齐。
- 每次更新把新 APK 提交推送到 master 的 `dist/cloud-genshin.apk`；安装器不再单独交付。
- 新代码不写中文注释（历史代码中的旧注释不管）。
- 不在 GitHub 上耗时；服务出错时按用户提供的凭证单次尝试，不反复试错。

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
