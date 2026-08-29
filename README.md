# Spine Player for Android

安卓端 Spine 模型播放器，界面和功能参考 Live2DViewerEX。支持动态壁纸、点击切换动画、ZIP 批量导入、多版本 Spine 兼容。

## 功能特性

- **Spine 模型播放**：支持 `.skel`（二进制）和 `.json`（文本）格式
- **动态壁纸**：基于 LibGDX `AndroidLiveWallpaperService`，低功耗渲染
- **点击切换动画**：触摸模型区域循环切换动画
- **ZIP 批量导入**：一个 ZIP 包含多个模型，自动扫描识别
- **多版本兼容**：架构支持 3.8 / 4.0 / 4.1 / 4.2 / 4.3，默认集成 4.2
- **模型自定义**：位置、缩放、背景颜色、帧率限制
- **动画选择**：下拉框切换任意动画

## 技术栈

- Kotlin + Gradle Kotlin DSL
- LibGDX 1.12.1（渲染引擎）
- spine-libgdx 4.2.12（Spine 运行时）
- AndroidX + Material Components
- minSdk 24 / targetSdk 34

## 项目结构

```
SpinePlayer/
├── app/
│   └── src/main/java/com/spineplayer/app/
│       ├── MainActivity.kt              # 主 Activity，底部导航
│       ├── SpineGameAdapter.kt          # LibGDX 渲染核心（预览+壁纸共用）
│       ├── SpineWallpaperService.kt     # 动态壁纸服务
│       ├── model/
│       │   ├── SpineModelInfo.kt        # 模型数据类
│       │   ├── ModelRepository.kt       # 模型持久化管理
│       │   └── ZipImporter.kt           # ZIP 导入器
│       ├── spine/
│       │   ├── SpineRenderer.kt         # 渲染器统一接口
│       │   ├── SpineRenderer42.kt       # 4.2 版本渲染器实现
│       │   ├── SpineRendererFactory.kt  # 渲染器工厂（版本分发）
│       │   ├── SpineVersion.kt          # 版本枚举
│       │   └── VersionDetector.kt       # 文件版本检测器
│       ├── ui/
│       │   ├── ModelListFragment.kt     # 模型列表
│       │   ├── PlayerFragment.kt        # 播放器预览
│       │   └── SettingsFragment.kt      # 设置页
│       └── util/
│           └── Preferences.kt           # 设置存储
└── README.md
```

## 构建步骤

### 1. 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK Platform 34
- Gradle 8.5（项目自带 wrapper）

### 2. 克隆并打开

```bash
# 用 Android Studio 打开项目根目录
# 等待 Gradle Sync 完成
```

### 3. 同步依赖

首次打开会自动下载：
- LibGDX 1.12.1
- spine-libgdx 4.2.12（来自 Maven Central）
- AndroidX / Material 组件

### 4. 构建 APK

```bash
./gradlew assembleDebug
# 输出: app/build/outputs/apk/debug/app-debug.apk
```

或在 Android Studio 中点击 **Build > Build Bundle(s) / APK(s) > Build APK(s)**

### 5. 安装到设备

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 使用方法

### 导入模型

1. 准备 ZIP 文件（格式见下文）
2. 打开 App → 模型页 → 点击右下角 **+** 按钮
3. 选择 ZIP 文件，自动解压并扫描模型
4. 点击模型卡片进入预览

### 设置为动态壁纸

1. 在模型页或播放器页点击 **设为壁纸**
2. 在系统壁纸选择器中选择 **Spine 动态壁纸**
3. 点击模型可切换动画

### ZIP 文件格式

一个 ZIP 中可包含多个模型，支持以下目录结构：

**扁平结构：**
```
models.zip
├── characterA.skel
├── characterA.atlas
├── characterA.png
├── characterB.json
├── characterB.atlas
└── characterB.png
```

**子目录结构：**
```
models.zip
├── characterA/
│   ├── characterA.skel
│   ├── characterA.atlas
│   └── characterA.png
└── characterB/
    ├── characterB.json
    ├── characterB.atlas
    └── characterB.png
```

每个模型需要三个文件：
| 文件 | 说明 |
|------|------|
| `.skel` 或 `.json` | 骨骼动画数据 |
| `.atlas` | 图集描述文件 |
| `.png` | 图集纹理图片（atlas 中引用） |

## 多版本 Spine 支持

### 核心问题

Spine runtime 的 `major.minor` 版本必须与导出数据的编辑器版本严格匹配，否则无法解析。不同版本的 runtime 类名相同（`com.esotericsoftware.spine.*`），不能直接同时依赖。

### 解决方案：包名隔离（Shade）

参考 Spine-Viewer-for-Android 作者的方案，使用 **Gradle Shadow Plugin** 将不同版本的 runtime relocate 到独立包名，实现共存。

### 默认支持

当前项目默认集成 **spine-libgdx 4.2.12**，可加载 4.2.x 和 4.3.x 模型。

### 添加 3.8 / 4.0 / 4.1 支持

#### 步骤 1：创建 Shadow Module

在项目根目录创建 `spine-v38/build.gradle.kts`：

```kotlin
plugins {
    id("java-library")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation("com.esotericsoftware.spine:spine-libgdx:3.8.99")
    implementation("com.badlogicgames.gdx:gdx:1.12.1")
}

tasks.shadowJar {
    // 将 spine runtime 重定位到独立包名
    relocate("com.esotericsoftware.spine", "com.spine.v38")
    // 排除 LibGDX（由主项目提供）
    exclude("com/badlogic/gdx/**")
    exclude("META-INF/**")
    
    archiveBaseName.set("spine-v38")
    archiveClassifier.set("")
}
```

#### 步骤 2：添加到 settings.gradle.kts

```kotlin
include(":spine-v38")
include(":spine-v40")
include(":spine-v41")
```

#### 步骤 3：主项目依赖

在 `app/build.gradle.kts` 中：

```kotlin
dependencies {
    implementation(project(":spine-v38"))
    implementation(project(":spine-v40"))
    implementation(project(":spine-v41"))
}
```

#### 步骤 4：创建对应版本渲染器

复制 `SpineRenderer42.kt` 为 `SpineRenderer38.kt`，修改：

```kotlin
// 包名改为 relocate 后的
import com.spine.v38.Animation
import com.spine.v38.AnimationState
import com.spine.v38.AnimationStateData
import com.spine.v38.Atlas
import com.spine.v38.Skeleton
import com.spine.v38.SkeletonBinary
import com.spine.v38.SkeletonJson
import com.spine.v38.SkeletonRenderer

class SpineRenderer38 : SpineRenderer {
    override val supportedVersion = SpineVersion.V3_8
    // ... 其余代码与 SpineRenderer42 一致
}
```

#### 步骤 5：注册到工厂

在 `SpineRendererFactory.kt` 中：

```kotlin
SpineVersion.V3_8 -> SpineRenderer38()
SpineVersion.V4_0 -> SpineRenderer40()
SpineVersion.V4_1 -> SpineRenderer41()
```

### 版本检测原理

- **JSON 格式**：读取文件头部，正则匹配 `"version": "x.x.xx"`
- **SKEL 二进制格式**：读取文件头（hash length → hash → version length → version string）

## 架构设计

### 渲染层抽象

```
SpineRenderer (接口)
    ├── SpineRenderer42 (4.2 runtime)
    ├── SpineRenderer38 (3.8 runtime, relocate 后)
    ├── SpineRenderer40 (4.0 runtime, relocate 后)
    └── SpineRenderer41 (4.1 runtime, relocate 后)

SpineRendererFactory 根据 VersionDetector 检测结果选择实现
```

### 壁纸与预览复用

`SpineGameAdapter` 继承 `ApplicationAdapter`，同时用于：
- `PlayerFragment` 中的 `AndroidFragmentApplication`（预览）
- `SpineWallpaperService` 的 `createListener()`（壁纸）

两者共享同一套加载、渲染、触摸逻辑。

### 触摸事件流

```
壁纸触摸 → LibGDX InputProcessor → SpineGameAdapter.handleTouch()
    → SpineRenderer.handleTouch()（命中检测）
    → 命中 → nextAnimation()（切换动画）
```

## 常见问题

### Q: 模型加载失败，提示版本不支持

A: 当前默认仅支持 4.2/4.3。3.8/4.0/4.1 需按上文「多版本 Spine 支持」添加对应模块。

### Q: 壁纸黑屏或不显示

A: 检查：
1. 模型文件是否完整（.skel/.json + .atlas + .png）
2. .atlas 文件中图片路径是否正确
3. 尝试在播放器页预览，确认模型能正常显示

### Q: 点击模型不切换动画

A: 检查设置页中「点击切换动画」是否开启。命中检测基于模型边界框，过小的模型可能需要调大缩放。

### Q: 如何降低壁纸功耗

A: 设置页中调低帧率限制（如 30 FPS），可显著降低功耗。

### Q: spine-libgdx 依赖下载失败

A: spine-libgdx 发布在 Maven Central，如网络问题可配置国内镜像：
```kotlin
// settings.gradle.kts
maven { url = uri("https://maven.aliyun.com/repository/public") }
```

## License

本项目代码仅供学习交流使用。Spine Runtime 遵循其原始许可证，使用 Spine 模型需遵守 Esoteric Software 的许可协议。
