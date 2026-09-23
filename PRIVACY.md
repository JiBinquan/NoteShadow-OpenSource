# 隐私与数据说明 / Privacy and data

[English](#english) · [返回中文 README](README.md)

本说明描述当前 `0.5.0` 候选版的实际数据行为。墨鱼笔记不要求账户，也没有集成分析或广告 SDK。

## 数据保存在何处

- 课程笔记、正式与草稿转写、导入的阅读文档、笔记图片、会话元数据和录音原件保存在应用专属存储中。
- 在 Android 10 及以上，完成的录音还会复制到公共 `Music/NoteShadow`，手动导出的文本和课程包保存在公共 `Download/NoteShadow`。较旧的系统通过保存选择器让用户决定导出位置，不创建固定位置的公共录音副本。
- 将记录移入回收站后仍可恢复；永久删除仅清理应用内该记录的文件。**公共录音副本和已导出文件不会随记录一起删除**，需要用户到文件管理器中另行删除。卸载应用会移除应用专属数据；请在卸载前自行导出需要保留的内容。

## 网络与模型

录音、阅读、普通笔记和本地语音识别无需联网。只有用户显式使用 `/lm` 等模型命令、在设置页测试连接或发现模型时，应用才会连接到用户配置的 HTTPS 模型接口。模型对话请求包含当前明确提交的问题以及同一课程此前成功的模型对话；录音、转写、阅读文档和无关笔记行不会自动加入请求。连接测试发送固定短提示，模型发现请求查询接口的模型列表。接收方如何处理请求由所选服务决定。

接口地址和模型参数保存在设备上；API Key 使用 Android Keystore 加密并存于不参与备份的应用目录。模型诊断记录仅保存有限的状态、耗时和错误类别，不保存问题、回答、密钥或接口地址。应用不内置 API Key。

## 系统权限与备份

麦克风权限用于录音和实时转写；前台服务与通知用于提示运行中的工作；唤醒锁用于维持录音或转写。网络权限仅供显式配置的模型功能使用。应用将系统云端备份设为关闭，并配置 Android 12 及以上的云备份和设备迁移排除规则。某些厂商设备的迁移实现可能有差异；这不影响用户主动导出或复制公共文件。

---

## English

[中文](#隐私与数据说明--privacy-and-data) · [English README](README.en.md)

This document describes the current `0.5.0` release candidate. NoteShadow requires no account and includes no analytics or advertising SDK.

### Storage and deletion

- Course notes, formal and draft transcripts, imported reading documents, note images, session metadata, and original recordings are stored in app-specific storage.
- On Android 10 and later, each completed recording is also copied to public `Music/NoteShadow`, and manually exported text and course packages are saved to public `Download/NoteShadow`. Older versions use a system save picker for exports and do not create a recording copy in a fixed public folder.
- Records in the trash can be restored. Permanent deletion removes only that record's app-private files. **Public recording copies and prior exports remain** and must be deleted separately in a file manager. Uninstalling the app removes app-specific data; export anything you want to keep first.

### Network and models

Recording, reading, ordinary notes, and local speech recognition work without a network connection. The app contacts a user-configured HTTPS model endpoint only when the user explicitly invokes a model command such as `/lm`, tests the connection, or discovers available models in Settings. A model conversation request includes the explicitly submitted question and previous successful model turns from the same course. Recordings, transcripts, reading documents, and unrelated note lines are not automatically included. The connection test sends a fixed short prompt; model discovery queries the provider's model list. The selected provider controls how it processes requests.

The endpoint and model settings are stored on the device. API keys are encrypted with Android Keystore in an app directory excluded from backup. Bounded diagnostic entries contain only status, duration, and error categories, not questions, replies, keys, or endpoint URLs. No API key is bundled with the app.

### Permissions and backup

Microphone access supports recording and live transcription. Foreground services and notifications signal active work; a wake lock supports ongoing recording or transcription. Network access supports only explicitly configured model features. System cloud backup is disabled, and Android 12+ cloud backup and device transfer rules exclude app data. Device migration behavior may vary by manufacturer; these settings do not control files that users export or copy to public storage.
