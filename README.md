# 墨鱼笔记 · NoteShadow

[English](README.en.md) · [更新记录](CHANGELOG.md) · [第三方组件](THIRD_PARTY_NOTICES.md)

![墨鱼笔记品牌横幅](docs/assets/readme/banner.png)

**在平板上录音、实时转写、整理笔记，并把课程资料留在自己手中。** 墨鱼笔记是一款面向课堂与会议的 Android 平板应用。录音和语音识别可在设备上离线完成；需要时，也可以主动调用自行配置的模型接口辅助写作。

> **项目状态**：`0.5.0` 源码和预览 APK 已公开，设备验证仍在进行。请先阅读[下载说明](#下载预览-apk)和[许可与分发](#许可与分发)。

## 一眼了解

| 实时转写 | 时间戳笔记 |
| --- | --- |
| ![实时转写界面](docs/assets/readme/live-transcription.jpg) | ![笔记编辑界面](docs/assets/readme/note-editor.jpg) |
| 边录音边查看本地转写，保留正式转写文本。 | 手动笔记与录音、转写并行；新行可自动标记时间。 |

| Markdown 预览 | 课程记录与导出 |
| --- | --- |
| ![Markdown 笔记预览](docs/assets/readme/markdown-preview.jpg) | ![课程记录详情](docs/assets/readme/record-detail.jpg) |
| 在应用内预览标题、列表、表格、代码和图片。 | 回看录音与转写，导出笔记包或完整课程包。 |

截图展示的是当前开发界面，最终发布版可能调整。

## 能做什么

- **录音与离线转写**：保存 AAC/M4A 录音；使用设备上的语音模型进行实时转写和录音文件转写。实时转写提供单线输出和可选的双线输出。默认的 Qwen 转写模式需要另外安装模型文件，见下文。
- **课程笔记**：在转写运行时写带时间戳的 Markdown 笔记，拍照或导入图片，切换编辑与只读预览。`/arc [n]` 可将最近的正式转写片段插入笔记，不调用在线模型。
- **整理与导出**：按项目管理课程记录，搜索转写、归档、放入回收站并恢复；按需导出正式转写、笔记与图片，或包含录音的完整课程包。
- **本地阅读**：导入 TXT/EPUB 文档，分别保存阅读进度。可选择在双线转写视图中临时混排阅读内容；导出的正式转写不包含这些内容。
- **可选模型对话**：在笔记中显式使用 `/lm` 等命令时，才向你配置的模型服务发送问题及本课程此前成功的模型对话。此功能需要网络和你自己的接口凭据。

## 隐私与数据

录音、离线语音识别、阅读和普通笔记无需账户或云端服务。使用可选模型功能时，应用会向所选服务发送你明确提交的问题及同一课程先前成功的模型对话；它不会自动上传录音、转写、阅读文档或其他笔记内容。API Key 由用户在设备上配置，不随 APK 提供。应用关闭系统云端备份；Android 12 及以上的设备迁移行为可能因厂商而异。Android 10 及以上会把完成的录音另存一份到公共 `Music/NoteShadow`；删除课程记录仅删除应用内副本，不会删除该公共副本或已导出的文件。详情见[隐私与数据说明](PRIVACY.md)。

## 下载预览 APK

[v0.5.0 预览版](https://github.com/JiBinquan/NoteShadow-OpenSource/releases/tag/v0.5.0)提供两种安装包：`full` 内含 Zipformer、SenseVoice 和 Silero VAD 模型，可以使用相应的离线转写方式；`lite` 不含这些模型，需按下文自行安装模型后才能使用离线转写。Qwen3-ASR 模型两包均需另行安装。两包使用同一应用 ID、版本号和签名证书，是同一应用的不同打包方式。发布页列有 SHA-256 校验值与第三方模型说明。

这是尚未完成设备回归测试的预览版。升级已有 v3 安装时请保留原应用数据，不要为解决签名冲突而卸载；安装前可核对发布页的签名证书指纹。

## 从源码构建

目前以 Windows PowerShell 构建流程为准。需要 Git LFS、JDK 11 或 17、Android SDK 31 和 Build Tools 31.0.0；应用最低支持 Android 6.0（API 23），随包的 sherpa-onnx 原生库为 ARM64。平板横屏是主要设计场景，其他设备形态尚需验证。

```powershell
git lfs install
git clone https://github.com/JiBinquan/NoteShadow-OpenSource.git
cd NoteShadow-OpenSource
```

在不提交到 Git 的 `local.properties` 中设置你自己的 SDK 路径，例如 `sdk.dir=C:/Android/Sdk`。首次构建需要下载依赖，使用：

```powershell
.\scripts\build.ps1 -Online
```

脚本会运行 JVM 单元测试并生成 Debug APK；依赖已有缓存时可省略 `-Online`。构建脚本要求项目路径和 Gradle 缓存路径仅含 ASCII 字符，详情见[测试文档](docs/engineering/TESTING.md)。Release 编译可运行 `.\scripts\build.ps1 -Tasks assembleRelease`；未配置本地签名材料时生成的 Release APK 不可直接作为正式更新包。更新已安装的应用必须使用相同应用 ID 和签名证书，并提高 `versionCode`。

**模型准备**：公开源码快照不包含语音模型或测试音频。离线转写需由用户自行取得有权使用的兼容模型，并放入设备的应用专属目录 `Android/data/com.noteshadow.app3/files/models/`：Qwen3-ASR INT8 放在 `qwen3-int8/`（实时正式转写及文件转写）；Zipformer INT8 放在 `zipformer-int8/`（实时草稿）；SenseVoice INT8 放在 `sensevoice-int8/`（兼容模式）；Silero VAD 放在 `silero-vad/`。所需文件名由 `LocalSherpaAsr` 和 `AudioFileTranscriber` 检查。缺少对应模型时，该转写方式无法启动；录音、笔记和记录管理仍可使用。模型许可与来源须由使用者独立确认。

## 项目文档与参与

- [架构说明](ARCHITECTURE.md) · [构建与测试](docs/engineering/TESTING.md) · [发布流程](docs/engineering/RELEASE_PROCESS.md)
- [更新记录](CHANGELOG.md) · [路线图](docs/ROADMAP.md) · [参与贡献](CONTRIBUTING.md) · [安全报告](SECURITY.md) · [问题反馈](https://github.com/JiBinquan/NoteShadow-OpenSource/issues)

提交改动前，请阅读 [AGENTS.md](AGENTS.md) 和[编码规范](docs/engineering/CODING_STANDARDS.md)。请不要在 Issue、截图或测试资料中上传真实录音、笔记或 API Key。

## 许可与分发

项目**自有代码和文档**采用 [Apache License 2.0](LICENSE)；本项目横幅、应用图标和演示截图采用 [CC BY 4.0](ASSETS_LICENSE.md)。第三方代码、原生库、模型和测试音频按各自条款处理，详见[第三方说明](THIRD_PARTY_NOTICES.md)。
