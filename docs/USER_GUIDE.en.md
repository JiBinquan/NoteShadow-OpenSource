# NoteShadow User Guide

[简体中文](USER_GUIDE.md) · [Project home](../README.en.md) · [Privacy and data](../PRIVACY.md)

![NoteShadow banner](assets/readme/banner.png)

This guide covers the **0.5.1 preview**. NoteShadow is designed primarily for Android tablets in landscape mode. It records audio, transcribes speech on the device, keeps course notes, reads TXT/EPUB documents, and organizes course records. The current app interface is in Chinese; this guide translates its labels and explains where to find them.

> **Before you start:** On-device regression testing is still pending. Keep original recordings and export important courses. The app requires Android 6.0 or later; its bundled speech-recognition native library targets ARM64 devices.

## 1. Install and prepare

1. Download one APK from the [v0.5.1 release](https://github.com/JiBinquan/NoteShadow-OpenSource/releases/tag/v0.5.1). `full` is about 1 GB and contains Qwen3-ASR, Zipformer, SenseVoice, and Silero VAD. `lite` is about 24 MB and contains no models.
2. Install the APK on Android. For an update, install the release-signed APK over the existing app. **Do not uninstall first**: uninstalling removes app-specific data. The release page lists SHA-256 checksums and the signing certificate fingerprint.
3. Grant the microphone and notification permissions requested when you first record or transcribe. Active recording and transcription show foreground-service notifications.
4. With `full`, first use of Qwen3 copies roughly 1 GB of model files into app-specific storage. Leave extra free space and allow loading to finish. Existing complete Qwen3 files are preserved. With `lite` or a source build without assets, prepare the models under [Model files](#11-model-files-and-troubleshooting).

Recording, ordinary notes, reading, and record management need no account or network connection. Optional model chat requires an HTTPS endpoint and API key that you configure.

## 2. Main screen

![Live transcription screen](assets/readme/live-transcription.jpg)

| Chinese label | What it does |
| --- | --- |
| Left navigation: 「实时转写」 / 「笔记」 | Switch between live transcription and course notes. Reopening the app or returning from the background defaults to the safe live-transcript page. |
| Top: 「单线输出」 / 「双线输出」 | Change the live display. The dual view can temporarily mix in reading text; the formal transcript is saved separately. |
| Top: 「记录」 | Open projects, past courses, archives, trash, and exports. |
| Top: 「新课」 | Finish the current course and start another after confirmation. The previous course remains in Records. |
| Top: 「更多」 | Open 「进度」 (document progress) or export the current transcript preview. |
| Top: 「设置」 | Choose recognition mode, screen timeout behavior, optional model endpoint, and storage views. |
| Bottom: 「录音」 / 「转写」 / 「文件转」 | Control recording, live transcription, and transcription of an existing audio file. Recording and live transcription have separate switches. |

The top action row scrolls horizontally. Tap the page title to switch quickly between the current page and the safe live-transcript page. Hardware keyboard shortcuts are **F8**, **Alt+Enter**, and **Ctrl+Shift+B**. This changes the display; it does not encrypt or delete content.

## 3. Record and transcribe live speech

1. Choose a live recognition mode in 「设置」. The default is 「实时草稿 + Qwen」 (live draft + Qwen). Stop recognition before changing modes.
2. Tap 「录音」 to save AAC/M4A audio; tap 「停止」 to stop. Long-press the recording button to open the most recent completed public recording. You can record without live transcription.
3. Tap 「转写」 to start on-device live recognition and 「停转写」 to stop. You can transcribe without recording. Initial model loading may take time.
4. Results appear on the live page; the formal transcript is saved with timestamps. To edit it, stop live transcription, open the dual-output view, tap 「编辑」, then tap 「完成」 when finished.
5. At the end of a course, tap 「新课」. The app stops current recording/transcription, saves the course, and clears the active transcript and note for the new course.

| Recognition mode in Settings | Models needed | Output |
| --- | --- | --- |
| 「仅 Qwen，省电」 (Qwen only) | Qwen3-ASR and Silero VAD | Qwen produces the formal transcript without loading the Zipformer draft model. |
| 「实时草稿 + Qwen」 (default) | Zipformer, Qwen3-ASR, and Silero VAD | Zipformer provides a quick draft; Qwen produces the formal transcript. Draft text does not replace formal text. |
| 「兼容模式（SenseVoice）」 | SenseVoice and Silero VAD | Uses the SenseVoice compatibility path. |

Use 「单线输出」 to focus on transcription. 「双线输出」 can temporarily mix text from the selected reading document into the display. Reading text is not saved in the formal transcript or its exports. Make sure recording and recognition are permitted in your setting.

## 4. Transcribe an existing audio file

1. On Android 10 or later, stop live recording and transcription, tap 「文件转」, and choose an audio file the device can decode. File transcription uses Qwen3-ASR regardless of the selected live mode.
2. The 「文件转写预览」 page shows timestamped segments as they are written. Tap 「暂停文件」 to pause. On return, choose 「继续当前」 (resume), 「选择其他文件」 (choose another), or 「从头重转」 (restart). Restarting creates new output and leaves the earlier output intact.
3. Results are saved in public `Download/NoteShadow`. 「更多 → 导出当前转写」 can also save the current preview text. Supported input formats depend on the device's audio decoder.

## 5. Course notes, images, and Markdown

![Course note editor](assets/readme/note-editor.jpg)

Tap 「笔记」 on the left to edit the current course note. New lines receive timestamps automatically. You can write while recording and transcription continue; navigating between pages does not stop background work. Notes use Markdown, including headings, lists, task items, quotes, dividers, emphasis, strikethrough, code, links, and tables.

- Tap 「拍照」 (camera) or 「图片」 (image) to add a photo or existing picture. The app inserts a relative Markdown image path and stores the image in that course's attachment directory.
- Tap 「预览」 for a read-only Markdown view, then 「编辑」 to return to the source. Preview supports images, tables, and code blocks.
- Type `/arc` on its own line and press Enter to insert the last **5** formal transcript lines. `/arc 12` requests any count from 1 to 200. This reads only the current course's formal transcript, not draft text, reading material, or a network model.

![Markdown preview](assets/readme/markdown-preview.jpg)

## 6. Optional online model chat

This feature is separate from on-device speech recognition. In 「设置 → 笔记问答 / 模型接口（OpenAI 兼容）」, import a key file or paste a key, enter an HTTPS endpoint, model ID, and any needed parameters, then tap 「保存模型接口」. If the endpoint supports `/models`, use 「发现模型」. 「测试模型连接」 sends only a fixed short test prompt. Settings also lets you clear keys and inspect diagnostic categories that do not contain question text.

In the **course-note editor**, enter a complete command line and press Enter:

| Command | Use |
| --- | --- |
| `/lm your question` | Send one question to the configured provider. `/ds your question` is a compatibility alias. |
| `/lmlbg` … `/lmled` | Start a multiline question and submit the intervening text with the end command. |
| `/lmcl` … `///` | Start continuous chat. Each ordinary nonempty note line is submitted as a question until `///` ends the mode. |
| `/lmnew` | Clear the current course's model conversation context and start a fresh conversation. |

Only these explicit actions send a question and **previous successful model turns from the same course** to the selected provider. Recordings, transcripts, reading documents, and other note lines are not automatically sent. The provider controls how it handles received content; avoid putting sensitive information in a question. Starting a new course also resets the model context.

## 7. TXT/EPUB bookshelf and reading progress

1. Tap 「更多 → 进度 → 添加 TXT / EPUB」 and select a local file. Each imported document has its own saved reading position.
2. Tap 「设为当前」 on a document card. 「设置进度」 accepts a percentage or character position. For EPUBs with multiple chapters, 「选择章节」 shows brief chapter-opening previews.
3. The current document can appear temporarily in dual-output transcription. It also supports note-page simulated typing: enter `/novst` on its own note line and press Enter. Subsequent English letter keys advance through the current document's text; spaces, Enter, and other characters behave normally. Enter `///` to end the mode. Select the document and check its position first so you do not insert unwanted text into the note.

Documents and progress stay on the device. Reading text is excluded from formal transcript exports. If simulated typing inserts text into the manual course note, that text becomes part of the note and is included when the note package is exported.

## 8. Records, projects, archives, and trash

![Course record details](assets/readme/record-detail.jpg)

- Tap 「记录」 for the course list. 「项目」 switches, creates, or renames a project. The search field searches transcript content. Use the archive toggle to switch between normal and archived records.
- Open a record to view its formal transcript, draft, course note, and recordings. You can rename it, play audio, archive it, or restore it. Some organizing actions require you to stop active recording or live transcription first.
- 「移入回收站」 moves the record's app-private data to a recoverable trash area. 「恢复到原项目」 restores its project, title, and archive state. Permanent deletion has two confirmation steps.
- On the Records screen, 「选择导出」 selects several courses for a transcript, note, or full-course package.

**Permanent deletion affects only that record inside the app.** Earlier exports and, on Android 10+, public copies in `Music/NoteShadow` remain. Check the file manager if you want to remove them too.

## 9. Exports and file locations

| Action | Contents |
| --- | --- |
| 「更多 → 导出当前转写」 | On Android 10+, save the currently displayed live-transcript or file-transcription preview as TXT. |
| Record details: 「导出正式转写」 | That course's formal transcript as TXT. |
| 「导出笔记包」 | ZIP with the Markdown course note and its images, without recordings. |
| 「导出完整课程包」 | ZIP that may contain formal and draft transcripts, the Markdown note, images, recordings, and a non-sensitive manifest. |
| Batch export from Records | Produce the corresponding transcript, note, or full-course package for selected records. |

On Android 10+, manual exports usually go to public `Download/NoteShadow`, while each completed recording also has a public copy in `Music/NoteShadow`. Older Android versions use a system save picker for exports and do not create a recording copy in a fixed public directory. 「设置 → 存储与目录」 shows current locations, storage usage, and shortcuts; it does not move files. Uninstalling removes app-specific data, so export courses you want to keep first. See [Privacy and data](../PRIVACY.md) for details.

## 10. Settings and routine checks

- **Recognition mode:** Select one of the three live paths. Stop active recognition before switching.
- **Keep screen on:** By default, the screen stays awake while recording or recognizing speech. Turning this off allows normal screen timeout but does not stop background work.
- **Model endpoint:** Used only for explicit online chat. Configure HTTPS URL, key, model ID, maximum output length, thinking parameter, and timeout; discover models or test the connection.
- **Local data / storage locations:** Inspect course storage use, default locations, and public-file shortcuts.
- **App information:** Check the installed version.

## 11. Model files and troubleshooting

`full` bundles these models. For `lite`, a public source build, or another installation without model assets, obtain compatible files you have the right to use and place them under the app-specific device directory `Android/data/com.noteshadow.app3/files/models/`. How a file manager accesses `Android/data` varies by Android version and manufacturer.

| Subdirectory | Required files | Purpose |
| --- | --- | --- |
| `qwen3-int8/` | `conv_frontend.onnx`, `encoder.int8.onnx`, `decoder.int8.onnx`, `tokenizer/vocab.json`, `tokenizer/merges.txt`, `tokenizer/tokenizer_config.json` | Formal live and file transcription. |
| `zipformer-int8/` | `model.int8.onnx`, `tokens.txt` | Live draft. |
| `sensevoice-int8/` | `sensevoice.int8.onnx`, `tokens.txt` | Compatibility mode. |
| `silero-vad/` | `silero_vad.onnx` | Live speech segmentation. |

- **Transcription will not start:** Check the models required by the selected mode, exact filenames, free space, and microphone permission. `full` needs extra space on Qwen3's first use; `lite` has no preinstalled models.
- **Signature mismatch during installation:** Confirm that the APK has the same application ID and signing certificate as the installed app. Do not treat uninstalling as the first fix: it removes app-specific data.
- **A deleted recording still appears:** Check public `Music/NoteShadow` copies and earlier files in `Download/NoteShadow`; deleting a record does not remove those copies.
- **Model chat fails:** Check HTTPS URL, key, model ID, and network access. Settings offers a connection test and diagnostic categories.
- **File transcription cannot resume:** Confirm the original audio file is still accessible from the system file picker; choose it again if needed.

For a reproducible problem, open an [issue](https://github.com/JiBinquan/NoteShadow-OpenSource/issues) with device model, Android version, app version, and reproduction steps. Do not upload real recordings, course notes, or API keys. Use the [private security reporting instructions](../SECURITY.md) for security issues.
