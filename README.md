# 云·原神 TV

TCL 智能电视（Amlogic T968 / Android 5.1 / armeabi-v7a）专用客户端，启动即加载电脑端云·原神网页。

## 模块

- `app` 主程序（GeckoView 内核，电脑端模式，虚拟鼠标与实体键盘适配）
- `installer` 轻量安装器（<10MB，经欢视助手传输，下载并安装主程序）

## 构建

```
python3 build_release.py
```

产物为根目录 `cloud-genshin.apk` 与 `cloud-genshin-installer.apk`。

## 安装

电视端经欢视助手安装 `dist/cloud-genshin-installer.apk`，运行后自动下载并安装主程序。
