# 虫潮猎手 · Swarm Hunter

<img src="docs/media/icon.png" width="120" alt="虫潮猎手应用图标" />

在真实空间中听声辨位、抬手出击的 PICO Spatial MR 虫群猎杀游戏。自定义你的发射手势，靠近悬浮猎具即可装备，再消灭从墙面和地面裂口爬出的蜘蛛与昆虫。

- **当前体验版**：1.0.12 debug（versionCode 13）
- **包名**：`com.example.swarmhunter`
- **安装包**：[GitHub Releases](https://github.com/DDDCC5/swarm-hunter/releases/tag/v1.0.12)
- **测试与限制**：[VERIFICATION.md](docs/VERIFICATION.md)

## 玩法

1. 阅读安全提示，放下手柄并允许手部及空间追踪权限。
2. 双手做一致的任意静态手势，保持约 2.5 秒，注册为发射动作。
3. 挥手或手势点击选择蛛丝、激光、能量弹或脉冲；可点击“重新注册手势”返回重录。
4. 将自然张开的双手分别靠近左右悬浮道具，自动装备，无需做发射动作。
5. 开始任务，听方位音寻找目标。先改变手型，再做注册手势发射，命中后计数增加。
6. 三条生命耗尽后结算击杀、累计存活时长与评级，可再次挑战。

面板持续跟随用户前方并保持竖直，已接取的工具使用稳定手部跟随；没有常驻辅助瞄准线。中文系统使用中文界面和语音，其他系统使用英文；应用内不混排两种语言。音量跟随系统。

## 功能与实现

- Kotlin 2.1.20、Compose、PICO Spatial SDK BOM 6.2.8。
- 官方手部追踪与自定义关节特征识别；注册容错、释放后重射及失追处理。
- 手掌/手腕近距离自动接取；工具跟随与手指发射识别分离。
- MR 透视、平面几何法线对齐、Portal 裂口进出及贴面移动。
- 原生物理投射体与连续路段射线检测；三命、短暂无敌、结算及重开。
- 5种蜘蛛、3种辅助昆虫、4种工具；28个GLB、170个音效及28个中英文语音WAV。
- 启动图标为全画布不透明正方形，由系统决定显示裁切形状。

以下是项目运行素材的离线渲染预览，不是实机战斗截图：

<img src="docs/media/spider-preview.png" width="420" alt="蜘蛛模型素材预览" />
<img src="docs/media/web-tool-preview.png" width="420" alt="蛛丝工具素材预览" />

## 构建

需要 JDK 17 或更新版本、Android SDK Platform 35 / 对应构建工具，以及可访问 PICO Spatial SDK Maven 仓库的环境。项目提供 Gradle 8.13 Wrapper。运行环境需为兼容 PICO Spatial SDK 6.2 的头显，arm64-v8a，Android API 35 及以上。

官方环境指南：[PICO CLI 安装与初始化](https://lf3-static.bytednsdoc.com/obj/eden-cn/bvonuhmlkbps/other/pico-cli-installation-guide.md)。按官方流程配置后，在 Android Studio 中打开本仓库，或运行：

```sh
./gradlew testDebugUnitTest assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。在自己的环境中配置 `ANDROID_HOME`，或创建未被版本控制跟踪的 `local.properties`。不要提交签名密钥或本机 SDK 路径。

官方 PICO CLI 安装与启动示例：

```sh
pico-cli device list --format json
pico-cli app install app/build/outputs/apk/debug/app-debug.apk --replace --device <device-id>
pico-cli app launch com.example.swarmhunter --device <device-id>
```

## 代码位置

| 路径 | 用途 |
| --- | --- |
| `app/src/main/java/com/example/swarmhunter/game/HuntRuntime.kt` | 设备追踪、场景、工具、音效与战斗集成 |
| `app/src/main/java/com/example/swarmhunter/game/HuntEngine.kt` | 游戏状态、手势记忆与纯逻辑 |
| `app/src/main/java/com/example/swarmhunter/game/HandAttachmentTracker.kt` | 工具跟随防抖 |
| `app/src/main/java/com/example/swarmhunter/game/PanelFollower.kt` | 前方水平朝向面板 |
| `app/src/main/java/com/example/swarmhunter/content/HomeStage.kt` | 自定义 Compose UI |
| `app/src/main/assets` | 应用使用的模型、UI与音频 |
| `app/src/test` | 本地逻辑回归测试 |

## 当前状态

37项核心测试通过。真机既有日志确认激光和蛛丝命中计数；最新版本安装、启动、原生平面朝向与连续碰撞检查通过。当前发布为**开发体验版**，端到端响应时间、感知声源定位精度、长期性能，以及最新面板/墙面视觉体验仍需专项验收；不把这些指标写成已达标承诺。

第三方依赖和素材说明见 [THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md)。本仓库公开源码，尚未指定项目整体的开源许可证。
