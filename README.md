# 云·原神 TV

TCL 智能电视（Amlogic T968 / Android 5.1 / armeabi-v7a）专用客户端，启动即加载电脑端云·原神网页。

> 上下文恢复 / 完整排查经验见 **[docs/KNOWLEDGE.md](docs/KNOWLEDGE.md)**。

## 模块

- `app` 主程序（GeckoView 内核，电脑端模式，虚拟鼠标与实体键盘适配）
- `installer` 轻量安装器（<10MB，经欢视助手传输，下载并安装主程序）

## 构建

```
python3 build_release.py
```

产物为 `dist/cloud-genshin.apk` 与 `dist/cloud-genshin-installer.apk`。

## 安装

电视端经欢视助手安装 `dist/cloud-genshin-installer.apk`，运行后自动下载并安装主程序。安装器固定从 master 的 `dist/cloud-genshin.apk` 下载，故更新主程序后需将新 APK 提交推送到 master。
