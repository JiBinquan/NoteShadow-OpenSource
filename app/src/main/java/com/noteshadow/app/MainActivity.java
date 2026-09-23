package com.noteshadow.app;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends Activity implements AsrEngine.Listener {
    private static final String ACTION_IMPORT_DS_KEY = "com.noteshadow.app.IMPORT_DS_KEY";
    private static final int REQ_AUDIO = 1002;
    private static final int PICK_AUDIO_FILE = 1003;
    private static final int PICK_NOTE_IMAGE = 1004;
    private static final int CAPTURE_NOTE_IMAGE = 1005;
    private static final String TAG = "NoteShadow";
    // A boss key must render in one frame. Keep a bounded, already-clean tail
    // instead of laying out a whole meeting document during the shortcut.
    private static final int MAX_BOSS_DISPLAY_CHARS = 24_000;

    private enum Mode { FAKE, REAL, TIMESTAMP_NOTE, BOSS, FILE_PREVIEW }
    private enum InputRedirectMode { NONE, LEGACY_FAKE_PAGE, NOTE_NOVEL }
    private static final int MAX_FILE_PREVIEW_CHARS = 80_000;

    private AppStorage storage;
    private BookshelfRepository bookshelf;
    private BookRecord activeBook;
    private ShadowEditText editor;
    private TextView title;
    private TextView status;
    private Button recordButton;
    private Button asrButton;
    private Button fileAsrButton;
    private Button editButton;
    private Button notePreviewButton;
    private Button notePhotoButton;
    private Button noteImageButton;
    private Button safePageButton;
    private MainPageView mainPageView;
    private NotePreviewView notePreviewView;
    private Mode mode = Mode.BOSS;
    private final PageNavigationPolicy pageNavigation = new PageNavigationPolicy();
    private String bookText = "";
    private int bookPos = 0;
    /** Stable base used to rebuild mixed output without shifting earlier novel slices. */
    private int mixedNovelStart = 0;
    private String fakeText = "";
    private String realText = "";
    // Display-only composition. Never persisted as the canonical transcript.
    private String mixedDisplayText = "";
    private int mixedProcessedLines;
    private String mixedEligibleRanges = "";
    private String bossText = "";
    private String timestampNoteText = "";
    private String realEditBase = "";
    private boolean editingRealTranscript;
    private int fakeSel = 0;
    private int realSel = 0;
    private int fakeScroll = 0;
    private int realScroll = 0;
    private int timestampNoteSel = 0;
    private int timestampNoteScroll = 0;
    private boolean formattingTimestampNote;
    private boolean insertingNovelText;
    private boolean timestampNotePreview;
    private boolean noteNovelInputActive;
    private NoteAttachmentRepository noteAttachments;
    private String pendingCameraSession = "";
    private String pendingCameraPath = "";
    private String pendingImageSession = "";
    private final NoteMediaReturnPolicy noteMediaReturn = new NoteMediaReturnPolicy();
    private DeepSeekCredentialStore dsCredentials;
    private LmContextStore lmContext;
    private static final ExecutorService noteImportWork = Executors.newSingleThreadExecutor();
    private static final ExecutorService lmRequests = Executors.newSingleThreadExecutor();
    private static final AtomicLong lmRequestIds = new AtomicLong();
    private final Object timestampNoteWriteLock = new Object();
    private volatile long timestampNoteRevision;
    private boolean recording;
    private boolean asrEnabled;
    private TranscriptionSettingsRepository transcriptionSettings;
    private LiveTranscriptionMode transcriptionMode;
    private boolean keepScreenOnDuringWork = true;
    private String partial = "";
    private String asrState = "";
    private String recordingSummary = "";
    private AsrEngine asrEngine;
    private AudioFileTranscriber fileTranscriber;
    private FileTranscriptionCheckpoint fileCheckpoint;
    private boolean fileTranscribing;
    private boolean activityForeground;
    private boolean resumeOnSafePage;
    private int fileCompletedSegments;
    private String fileAsrStatus = "";
    private String filePreviewText = "";
    private boolean viewLoaded;
    private final android.os.Handler saveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable bookProgressSaveRunnable = this::persistActiveBookProgress;

    private final BroadcastReceiver recordingReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            recording = intent.getBooleanExtra(RecordingService.EXTRA_RECORDING, false);
            String error = intent.getStringExtra(RecordingService.EXTRA_ERROR);
            String path = intent.getStringExtra(RecordingService.EXTRA_PATH);
            String publicLocation = intent.getStringExtra(RecordingService.EXTRA_PUBLIC_LOCATION);
            if (error != null && !error.isEmpty()) toast("录音启动失败：" + error);
            if (!recording && error == null && path != null && !path.isEmpty()) {
                File file = new File(path);
                recordingSummary = file.isFile() && file.length() > 0
                        ? (publicLocation == null ? "录音已保存 " : "已保存到 " + publicLocation + " · ") + readableSize(file.length())
                        : "录音保存失败";
                toast(recordingSummary);
            }
            refreshStatus();
        }
    };

    private final BroadcastReceiver transcriptionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String kind = intent.getStringExtra(RealtimeTranscriptionService.EXTRA_KIND);
            String text = intent.getStringExtra(RealtimeTranscriptionService.EXTRA_TEXT);
            if (RealtimeTranscriptionService.KIND_PARTIAL.equals(kind)) {
                partial = text == null ? "" : text;
            } else if (RealtimeTranscriptionService.KIND_STATE.equals(kind)) {
                asrState = text == null ? "" : text;
                if (asrState.contains("不可用") || asrState.contains("失败")) asrEnabled = false;
            } else if (RealtimeTranscriptionService.KIND_FINAL.equals(kind)) {
                partial = "";
                realText = storage.realNote();
                recordNewMixedLines();
                bossText = boundedBossText(bossDisplayText(realText));
                if (mode == Mode.REAL && editor != null) {
                    renderMixedTranscript(false);
                } else if (mode == Mode.BOSS && editor != null) {
                    renderBossAppend();
                    editor.setSelection(editor.length());
                }
            }
            refreshStatus();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        storage = new AppStorage(this);
        bookshelf = new BookshelfRepository(getFilesDir());
        try {
            bookshelf.migrateLegacy(storage.bookName(), storage.loadBook(), storage.bookPos(),
                    storage.bookChapter(), storage.chapterStarts(), System.currentTimeMillis());
        } catch (Exception migrationFailure) {
            Log.e(TAG, "Bookshelf migration failed; keeping legacy reading state", migrationFailure);
        }
        noteAttachments = new NoteAttachmentRepository(this);
        if (state != null) {
            pendingCameraSession = state.getString("pending_camera_session", "");
            pendingCameraPath = state.getString("pending_camera_path", "");
            pendingImageSession = state.getString("pending_image_session", "");
        }
        lmContext = new LmContextStore(this, storage.currentSessionId());
        dsCredentials = new DeepSeekCredentialStore(this);
        if (getIntent() != null && ACTION_IMPORT_DS_KEY.equals(getIntent().getAction())) {
            importStagedDsKey();
        }
        // Legacy fake-page data remains readable, but the visible two-line mode is mixed transcript.
        loadActiveBookAtStartup();
        mixedNovelStart = BookProgress.clampToCodePointBoundary(bookText,
                storage.mixedNovelStartInitialized() ? storage.mixedNovelStart() : bookPos);
        if (!storage.mixedNovelStartInitialized()) storage.setMixedNovelStart(mixedNovelStart);
        if (!storage.mixedNovelEndInitialized()) storage.setMixedNovelEnd(mixedNovelStart);
        fakeText = storage.fakeNote();
        realText = storage.realNote();
        timestampNoteText = storage.timestampNote();
        initializeMixedPolicy();
        applyMixedResult(MixedTranscriptPresenter.buildResult(
                realText, bookText, mixedNovelStart, mixedEligibleRanges));
        bossText = boundedBossText(bossDisplayText(realText));
        fakeSel = storage.fakeSelection();
        realSel = storage.realSelection();
        fakeScroll = storage.fakeScroll();
        realScroll = storage.realScroll();
        timestampNoteSel = storage.timestampNoteSelection();
        timestampNoteScroll = storage.timestampNoteScroll();
        // A persisted true can be stale after force-stop/reboot. A live foreground
        // service shares this process and exposes the authoritative state.
        recording = RecordingService.isActive();
        storage.setRecording(recording);
        String previousRecording = storage.lastRecording();
        if (!recording && !storage.publicRecordingUri().isEmpty() && previousRecording != null && !previousRecording.isEmpty()) {
            File previous = new File(previousRecording);
            recordingSummary = "上次录音：音乐/NoteShadow/" + previous.getName()
                    + (previous.isFile() ? " · " + readableSize(previous.length()) : "");
        }
        transcriptionSettings = new TranscriptionSettingsRepository(this);
        // Import the old Activity-private switch once, then use the shared setting.
        android.content.SharedPreferences asrPrefs = getPreferences(MODE_PRIVATE);
        transcriptionMode = transcriptionSettings.migrateLegacyIfNeeded(
                asrPrefs.getBoolean("quality_asr", false));
        asrPrefs.edit().putBoolean("hybrid_mode_migrated", true).apply();
        keepScreenOnDuringWork = getSharedPreferences(ActiveWorkPolicy.PREFERENCES,
                MODE_PRIVATE).getBoolean(ActiveWorkPolicy.KEEP_SCREEN_ON, true);
        asrEngine = new LocalSherpaAsr(this, this, transcriptionMode);
        asrEnabled = RealtimeTranscriptionService.isActive();
        if (asrEnabled) asrState = "后台实时转写中";
        fileCheckpoint = new FileTranscriptionCheckpoint(this);
        fileTranscriber = new AudioFileTranscriber(this, new AudioFileTranscriber.Listener() {
            @Override public void onState(String text) {
                fileAsrStatus = text == null ? "" : text;
                refreshStatus();
            }

            @Override public void onProgress(long decodedUs, long durationUs, int completedSegments) {
                if (durationUs > 0) {
                    int percent = (int)Math.min(100, Math.max(0, decodedUs * 100L / durationUs));
                    fileAsrStatus = "文件转写 " + percent + "% · 已完成 " + completedSegments + " 句";
                } else {
                    fileAsrStatus = "文件转写中 · 已完成 " + completedSegments + " 句";
                }
                refreshStatus();
            }

            @Override public void onDestinationCreated(String outputUri, String publicLocation) {
                fileCheckpoint.outputCreated(outputUri, publicLocation);
            }

            @Override public void onTranscriptLine(String line, long startUs, long endUs) {
                fileCompletedSegments++;
                fileCheckpoint.checkpoint(endUs, fileCompletedSegments);
                appendFilePreview(line + "\n");
            }

            @Override public void onPaused(String publicLocation, int completedSegments) {
                fileTranscribing = false;
                fileCompletedSegments = completedSegments;
                fileCheckpoint.pause();
                updateKeepScreenOn();
                fileAsrStatus = "文件转写已暂停 · 已完成 " + completedSegments + " 句 · 点文件转继续";
                appendFilePreview("\n—— 已暂停，完整文本已保留到 " + publicLocation + " ——\n");
                refreshStatus();
                toast("已暂停；点“文件转”即可继续");
            }

            @Override public void onCompleted(String publicLocation, int completedSegments) {
                fileTranscribing = false;
                fileCompletedSegments = completedSegments;
                fileCheckpoint.complete();
                updateKeepScreenOn();
                fileAsrStatus = "文件转写完成 · " + completedSegments + " 句 · " + publicLocation;
                appendFilePreview("\n—— 转写完成，完整文本已保存到 " + publicLocation + " ——\n");
                refreshStatus();
                toast("转写已保存到 " + publicLocation);
            }

            @Override public void onCancelled(String partialLocation, int completedSegments) {
                fileTranscribing = false;
                fileCompletedSegments = completedSegments;
                fileCheckpoint.pause();
                updateKeepScreenOn();
                fileAsrStatus = "文件转写已取消 · 已保留 " + completedSegments + " 句";
                appendFilePreview("\n—— 已取消，已完成内容已保留 ——\n");
                refreshStatus();
                toast("已取消；临时结果位于 " + partialLocation);
            }

            @Override public void onFailed(String message) {
                fileTranscribing = false;
                fileCheckpoint.fail();
                updateKeepScreenOn();
                fileAsrStatus = "文件转写失败";
                appendFilePreview("\n—— 文件转写失败：" + message + " ——\n");
                refreshStatus();
                toast("文件转写失败：" + message);
            }
        });
        buildUi();
        // Always enter through the transcript-only safe page. The previous
        // visible mode may contain reading material and must not be restored
        // after a lock-screen recreation or a cold launch.
        resetToSafePage(false);
        if (fileCheckpoint.canResume()) {
            fileCompletedSegments = fileCheckpoint.completed();
            fileAsrStatus = "有未完成文件转写 · 已完成 " + fileCompletedSegments + " 句 · 点文件转继续";
        }
        registerReceiverCompat();
        new Thread(this::validateLastRecording, "recording-check").start();
        consumeBookImportIntent(getIntent());
    }

    private void buildUi() {
        MainPageView pageView = new MainPageView(this);
        mainPageView = pageView;
        LinearLayout root = pageView.root();
        title = pageView.title();
        status = pageView.status();
        recordButton = pageView.recordButton();
        asrButton = pageView.asrButton();
        fileAsrButton = pageView.fileAsrButton();
        editButton = pageView.editButton();
        notePreviewButton = pageView.previewButton();
        notePhotoButton = pageView.photoButton();
        noteImageButton = pageView.imageButton();
        safePageButton = pageView.safePageButton();

        title.setOnClickListener(v -> toggleBossMode());
        title.setOnLongClickListener(v -> { toggleBossMode(); return true; });
        recordButton.setOnClickListener(v -> toggleRecording());
        recordButton.setOnLongClickListener(v -> { openLastRecording(); return true; });
        asrButton.setOnClickListener(v -> toggleAsr());
        asrButton.setOnLongClickListener(v -> { toggleAsrMode(); return true; });
        fileAsrButton.setOnClickListener(v -> toggleFileTranscription());
        editButton.setOnClickListener(v -> toggleRealTranscriptEditing());
        notePreviewButton.setOnClickListener(v -> toggleTimestampNotePreview());
        notePhotoButton.setOnClickListener(v -> captureNotePhoto());
        noteImageButton.setOnClickListener(v -> chooseNoteImage());
        safePageButton.setOnClickListener(v -> selectMainPage(PageNavigationPolicy.Page.TRANSCRIPT, false));
        pageView.singleOutputButton().setOnClickListener(v -> selectMainPage(PageNavigationPolicy.Page.TRANSCRIPT, false));
        pageView.dualOutputButton().setOnClickListener(v -> {
            selectMainPage(PageNavigationPolicy.Page.DUAL_TRANSCRIPT, false);
        });
        pageView.recordsButton().setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        pageView.newLessonButton().setOnClickListener(v -> confirmNewLesson());
        pageView.moreButton().setOnClickListener(v -> showMoreMenu());
        pageView.settingsButton().setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        pageView.noteButton().setOnClickListener(v -> selectMainPage(PageNavigationPolicy.Page.TIMESTAMP_NOTE, false));

        status.setOnClickListener(v -> {
            if (fileTranscribing || mode == Mode.FILE_PREVIEW) showFilePreview();
            else if (mode == Mode.REAL && !storage.publicRecordingUri().isEmpty()) openLastRecording();
            else showReadingProgress();
        });
        editor = new ShadowEditText(this);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTextSize(18);
        editor.setTextColor(getResources().getColor(R.color.graphite_text_primary));
        editor.setBackgroundColor(getResources().getColor(R.color.graphite_surface));
        editor.setPadding(dp(18), dp(18), dp(18), dp(18));
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setLineSpacing(dp(3), 1.05f);
        editor.setOnFocusChangeListener((v, hasFocus) -> updateImeMode());
        editor.addTextChangedListener(new android.text.TextWatcher() {
            private int insertedStart = -1;
            private String insertedText = "";
            private boolean insertedFromNovel;

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mode == Mode.TIMESTAMP_NOTE && !formattingTimestampNote && count > 0) {
                    String inserted = s.subSequence(start, start + count).toString();
                    if (inserted.indexOf('\n') >= 0 || inserted.indexOf('\r') >= 0) {
                        insertedStart = start;
                        insertedText = inserted;
                        insertedFromNovel = insertingNovelText;
                    }
                }
            }
            @Override public void afterTextChanged(Editable s) {
                if (!formattingTimestampNote && insertedStart >= 0) {
                    int start = insertedStart;
                    String inserted = insertedText;
                    boolean fromNovel = insertedFromNovel;
                    insertedStart = -1;
                    insertedText = "";
                    insertedFromNovel = false;
                    String stamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                    String formatted = TimestampNoteFormatter.formatInsertedText(inserted, stamp);
                    formattingTimestampNote = true;
                    try {
                        s.replace(start, start + inserted.length(), formatted);
                        editor.setSelection(Math.min(start + formatted.length(), editor.length()));
                    } finally {
                        formattingTimestampNote = false;
                    }
                    if (!fromNovel && ("\n".equals(inserted) || "\r".equals(inserted)
                            || "\r\n".equals(inserted))) {
                        runNoteCommandFromNewline(s, start);
                    }
                }
                scheduleSave();
            }
        });
        notePreviewView = new NotePreviewView(this, noteAttachments);

        FrameLayout noteSurface = new FrameLayout(this);
        noteSurface.addView(editor, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        noteSurface.addView(notePreviewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageView.contentHost().addView(noteSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void registerReceiverCompat() {
        IntentFilter f = new IntentFilter(RecordingService.ACTION_STATE);
        // targetSdk 31: package-scoped broadcasts remain safe and compile on the installed SDK.
        registerReceiver(recordingReceiver, f);
        registerReceiver(transcriptionReceiver, new IntentFilter(RealtimeTranscriptionService.ACTION_EVENT));
    }

    private void loadActiveBookAtStartup() {
        try {
            activeBook = bookshelf.current();
            if (activeBook != null) {
                bookText = bookshelf.loadText(activeBook);
                bookPos = BookProgress.clampToCodePointBoundary(bookText, activeBook.getPosition());
                return;
            }
        } catch (Exception failure) {
            Log.e(TAG, "Unable to load bookshelf current item", failure);
        }
        activeBook = null;
        bookText = storage.loadBook();
        bookPos = BookProgress.clampToCodePointBoundary(bookText, storage.bookPos());
    }

    private void reloadSelectedBook() {
        try {
            BookRecord selected = bookshelf.current();
            String oldId = activeBook == null ? "" : activeBook.getId();
            String newId = selected == null ? "" : selected.getId();
            int selectedPosition = selected == null ? 0 : selected.getPosition();
            boolean changed = !oldId.equals(newId) || selectedPosition != bookPos;
            if (!changed) return;
            String selectedText = selected == null ? "" : bookshelf.loadText(selected);
            int nextPosition = BookProgress.clampToCodePointBoundary(selectedText, selectedPosition);
            noteNovelInputActive = false;
            activeBook = selected;
            bookText = selectedText;
            bookPos = nextPosition;
            mixedNovelStart = bookPos;
            storage.setMixedNovelStart(bookPos);
            storage.setMixedNovelEnd(bookPos);
            mixedProcessedLines = MixedTranscriptPresenter.lineCount(realText);
            mixedEligibleRanges = "";
            storage.setMixedPolicy(mixedProcessedLines, mixedEligibleRanges);
            syncLegacyBookMirror();
            applyMixedResult(MixedTranscriptPresenter.buildResult(
                    realText, bookText, mixedNovelStart, mixedEligibleRanges));
            if (editor != null) updateImeMode();
            refreshStatus();
        } catch (Exception failure) {
            Log.e(TAG, "Unable to switch bookshelf item", failure);
            toast("当前资料加载失败，仍保留原阅读状态");
        }
    }

    private int activeBookChapter() {
        return activeBook == null ? storage.chapterForPosition(bookPos)
                : bookshelf.chapterForPosition(activeBook, bookPos);
    }

    private void persistActiveBookProgress() {
        int chapter = activeBookChapter();
        if (activeBook != null) try {
            activeBook = bookshelf.updateProgress(activeBook.getId(), bookPos, chapter, bookText);
            chapter = activeBook.getChapter();
        } catch (Exception failure) {
            Log.e(TAG, "Unable to persist bookshelf progress", failure);
        }
        storage.setBookPos(bookPos);
        storage.setBookChapter(chapter);
    }

    private void scheduleActiveBookProgressSave() {
        storage.setBookPos(bookPos);
        storage.setBookChapter(activeBookChapter());
        saveHandler.removeCallbacks(bookProgressSaveRunnable);
        saveHandler.postDelayed(bookProgressSaveRunnable, 350);
    }

    private void syncLegacyBookMirror() {
        if (activeBook == null) return;
        storage.syncActiveBook(activeBook.getTitle(), bookText, bookPos,
                bookshelf.chapterForPosition(activeBook, bookPos), activeBook.getChapterStarts());
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAPTURE_NOTE_IMAGE) {
            restoreNotePageAfterMediaResult();
            finishNotePhoto(resultCode == RESULT_OK);
            return;
        }
        if (requestCode == PICK_NOTE_IMAGE) {
            restoreNotePageAfterMediaResult();
            String expectedSession = pendingImageSession;
            pendingImageSession = "";
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                importNoteImage(expectedSession, data.getData());
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == PICK_AUDIO_FILE) importAudioUri(data.getData());
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("pending_camera_session", pendingCameraSession);
        state.putString("pending_camera_path", pendingCameraPath);
        state.putString("pending_image_session", pendingImageSession);
        super.onSaveInstanceState(state);
    }

    private void captureNotePhoto() {
        if (mode != Mode.TIMESTAMP_NOTE || timestampNotePreview) {
            toast("请先在笔记编辑页拍照");
            return;
        }
        saveCurrentNow();
        String sessionId = storage.currentSessionId();
        try {
            File target = noteAttachments.createCameraTarget(sessionId);
            String relative = "attachments/" + target.getName();
            pendingCameraSession = sessionId;
            pendingCameraPath = relative;
            Uri uri = NoteAttachmentProvider.uriFor(this, sessionId, target);
            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            camera.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            camera.setClipData(ClipData.newRawUri("课堂照片", uri));
            noteMediaReturn.onLaunch();
            startActivityForResult(camera, CAPTURE_NOTE_IMAGE);
        } catch (Throwable failure) {
            noteMediaReturn.onLaunchFailed();
            clearPendingCamera(true);
            toast("无法打开相机");
        }
    }

    private void chooseNoteImage() {
        if (mode != Mode.TIMESTAMP_NOTE || timestampNotePreview) {
            toast("请先在笔记编辑页导入图片");
            return;
        }
        saveCurrentNow();
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("image/*");
        pendingImageSession = storage.currentSessionId();
        noteMediaReturn.onLaunch();
        try { startActivityForResult(pick, PICK_NOTE_IMAGE); }
        catch (Throwable failure) {
            noteMediaReturn.onLaunchFailed();
            pendingImageSession = "";
            toast("无法打开图片选择器");
        }
    }

    private void finishNotePhoto(boolean accepted) {
        String sessionId = pendingCameraSession;
        String pendingPath = pendingCameraPath;
        File source = noteAttachments.resolve(sessionId, pendingPath);
        try {
            if (!accepted || source == null || !sessionId.equals(storage.currentSessionId())) return;
            String relative = noteAttachments.finishCameraTarget(sessionId, source);
            File saved = noteAttachments.resolve(sessionId, relative);
            if (!appendNoteImage(sessionId, relative, "照片") && saved != null)
                noteAttachments.deleteIfOwned(sessionId, saved);
        } catch (Exception failure) {
            toast("照片保存失败");
        } finally {
            if (source != null) noteAttachments.deleteIfOwned(sessionId, source);
            try {
                if (source != null) revokeUriPermission(
                        NoteAttachmentProvider.uriFor(this, sessionId, source),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Throwable ignored) { }
            pendingCameraSession = "";
            pendingCameraPath = "";
        }
    }

    private void restoreNotePageAfterMediaResult() {
        if (!noteMediaReturn.onResult()) return;
        if (activityForeground) {
            noteMediaReturn.consumeRestore();
            selectMainPage(PageNavigationPolicy.Page.TIMESTAMP_NOTE, false);
        }
    }

    private void clearPendingCamera(boolean delete) {
        File source = noteAttachments == null ? null
                : noteAttachments.resolve(pendingCameraSession, pendingCameraPath);
        if (delete && source != null) noteAttachments.deleteIfOwned(pendingCameraSession, source);
        pendingCameraSession = "";
        pendingCameraPath = "";
    }

    private void importNoteImage(String expectedSession, Uri uri) {
        if (expectedSession == null || expectedSession.isEmpty() || uri == null) return;
        String mime = getContentResolver().getType(uri);
        String name = queryName(uri);
        noteImportWork.execute(() -> {
            String relative = null;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                relative = noteAttachments.importImage(expectedSession, input, mime, name);
                final String saved = relative;
                runOnUiThread(() -> {
                    File attachment = noteAttachments.resolve(expectedSession, saved);
                    if (!appendNoteImage(expectedSession, saved, "图片") && attachment != null)
                        noteAttachments.deleteIfOwned(expectedSession, attachment);
                });
            } catch (Exception failure) {
                if (relative != null) {
                    File attachment = noteAttachments.resolve(expectedSession, relative);
                    if (attachment != null) noteAttachments.deleteIfOwned(expectedSession, attachment);
                }
                runOnUiThread(() -> toast("图片导入失败；请选择有效且不超过 25 MB 的图片"));
            }
        });
    }

    private boolean appendNoteImage(String expectedSession, String relativePath, String alt) {
        if (!expectedSession.equals(storage.currentSessionId())) return false;
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String current = storage.timestampNote();
        String markdown = "![" + alt + "](" + relativePath + ")";
        String next;
        if (current.matches("(?s).*\\[\\d{2}:\\d{2}:\\d{2}\\] $")) {
            next = current + markdown + "\n" + TimestampNoteFormatter.bracketedLabel(stamp);
        } else {
            next = current + (current.isEmpty() || current.endsWith("\n") ? "" : "\n")
                    + TimestampNoteFormatter.bracketedLabel(stamp) + markdown + "\n"
                    + TimestampNoteFormatter.bracketedLabel(stamp);
        }
        if (!storage.saveTimestampNoteIfCurrent(expectedSession, storage.fakeNote(),
                storage.realNote(), next, storage.bookPos(), storage.bookChapter())) return false;
        timestampNoteText = next;
        timestampNoteSel = next.length();
        timestampNoteRevision++;
        if (mode == Mode.TIMESTAMP_NOTE && editor != null) {
            setEditorTextProgrammatically(next);
            editor.setSelection(editor.length());
            refreshTimestampNotePreviewIfVisible();
        }
        toast("图片已加入课堂笔记");
        return true;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && ACTION_IMPORT_DS_KEY.equals(intent.getAction())) {
            importStagedDsKey();
            return;
        }
        consumeBookImportIntent(intent);
    }

    private void consumeBookImportIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        intent.setData(null);
        importBookUri(uri);
    }

    private void importStagedDsKey() {
        try { dsCredentials.importStaged(this); }
        catch (Exception ignored) { toast("密钥导入失败，请在设置中重试"); }
    }

    private void importBookUri(Uri uri) {
        final String name = queryName(uri);
        final String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".txt") && !lower.endsWith(".epub")) {
            toast("导入失败：请选择 .txt 或 .epub 文件");
            return;
        }
        new Thread(() -> {
            try {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IllegalArgumentException("无法打开文件");
                    bookshelf.importBook(in, name);
                }
                runOnUiThread(() -> {
                    reloadSelectedBook();
                    toast("已加入资料库：" + name);
                });
            } catch (Throwable e) {
                Log.e(TAG, "Book import failed", e);
                runOnUiThread(() -> toast("导入失败：" + e.getMessage()));
            }
        }, "book-import").start();
    }

    private void toggleFileTranscription() {
        if (fileTranscribing) {
            fileTranscriber.pause();
            fileAsrStatus = "正在暂停文件转写";
            refreshStatus();
            return;
        }
        if (recording || asrEnabled) {
            toast("请先停止实时录音和转写，再处理录音文件");
            return;
        }
        if (fileCheckpoint.canResume()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("有未完成的文件转写")
                    .setMessage("可继续当前文件，也可保留它的断点并选择另一份录音。")
                    .setPositiveButton("继续当前", (d, w) -> resumeFileTranscription())
                    .setNegativeButton("选择其他文件", (d, w) -> chooseAudioFile())
                    .setNeutralButton("从头重转", (d, w) -> restartCurrentFileTranscription())
                    .show();
            return;
        }
        chooseAudioFile();
    }

    private void chooseAudioFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        // Keep Huawei DocumentsUI from hiding M4A/MP3 files with inconsistent MIME labels.
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_AUDIO_FILE);
    }

    private void importAudioUri(Uri uri) {
        try {
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Throwable ignored) {}
            String name = queryName(uri);
            filePreviewText = "# " + name + "\n# 本地离线 Qwen 转写\n\n正在读取录音文件…\n";
            showMode(Mode.FILE_PREVIEW, false);
            fileCompletedSegments = 0;
            fileCheckpoint.begin(uri.toString(), name);
            fileTranscribing = fileTranscriber.start(uri, name);
            if (!fileTranscribing) {
                toast("已有文件转写任务正在运行；请先点“暂停文件”");
                return;
            }
            updateKeepScreenOn();
            fileAsrStatus = "正在准备文件转写：" + name;
            refreshStatus();
        } catch (Throwable e) {
            fileTranscribing = false;
            updateKeepScreenOn();
            fileAsrStatus = "文件转写无法启动";
            refreshStatus();
            toast("无法开始文件转写：" + e.getMessage());
        }
    }

    private void resumeFileTranscription() {
        try {
            Uri uri = Uri.parse(fileCheckpoint.sourceUri());
            String name = fileCheckpoint.name();
            fileCompletedSegments = fileCheckpoint.completed();
            filePreviewText = "# " + name + "\n# 本地离线 Qwen 转写\n\n"
                    + "正在从已完成的第 " + fileCompletedSegments + " 句继续…\n";
            showMode(Mode.FILE_PREVIEW, false);
            fileTranscribing = fileTranscriber.start(uri, name, fileCheckpoint.endUs(),
                    fileCheckpoint.outputUri(), fileCompletedSegments);
            if (!fileTranscribing) {
                toast("已有文件转写任务正在运行；请先点“暂停文件”");
                return;
            }
            updateKeepScreenOn();
            fileAsrStatus = "正在从断点继续 · 已完成 " + fileCompletedSegments + " 句";
            refreshStatus();
        } catch (Throwable e) {
            fileTranscribing = false;
            updateKeepScreenOn();
            fileAsrStatus = "无法恢复文件转写";
            refreshStatus();
            toast("无法恢复：" + e.getMessage());
        }
    }

    /** Starts a fresh fixed-5-second transcription of the same source; old output remains untouched. */
    private void restartCurrentFileTranscription() {
        try {
            Uri uri = Uri.parse(fileCheckpoint.sourceUri());
            String name = fileCheckpoint.name();
            fileCheckpoint.complete();
            importAudioUri(uri);
            toast("已按新 5 秒方案从头重转：" + name);
        } catch (Throwable e) {
            toast("无法从头重转：" + e.getMessage());
        }
    }

    /** Exports canonical ASR text; the temporary novel presentation is never an export source. */
    private void exportCurrentTranscriptPreview() {
        if (mode != Mode.REAL && mode != Mode.BOSS && mode != Mode.FILE_PREVIEW) {
            toast("请先切到转写记录或文件转写预览页面");
            return;
        }
        if (Build.VERSION.SDK_INT < 29) {
            toast("当前 Android 版本不支持直接导出到下载目录");
            return;
        }
        final String text;
        if (mode == Mode.FILE_PREVIEW) text = editor == null ? "" : editor.getText().toString();
        else if (mode == Mode.BOSS) text = bossDisplayText(storage.realNote());
        else text = storage.realNote();
        if (text.trim().isEmpty()) {
            toast("当前没有可导出的转写文本");
            return;
        }
        final boolean filePreview = mode == Mode.FILE_PREVIEW;
        toast("正在导出预览文本…");
        new Thread(() -> {
            Uri uri = null;
            try {
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String name = (filePreview ? "文件转写预览_" : "实时转写预览_") + stamp + ".txt";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/NoteShadow");
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("无法创建导出文件");
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new IllegalStateException("无法写入导出文件");
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                final String location = "下载/NoteShadow/" + name;
                runOnUiThread(() -> toast("预览已导出到 " + location));
            } catch (Throwable e) {
                if (uri != null) try { getContentResolver().delete(uri, null, null); } catch (Throwable ignored) {}
                final String message = e.getMessage() == null ? "未知错误" : e.getMessage();
                runOnUiThread(() -> toast("导出失败：" + message));
            }
        }, "export-transcript-preview").start();
    }

    private String queryName(Uri uri) {
        android.database.Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } finally { if (c != null) c.close(); }
        String p = uri.getLastPathSegment();
        return p == null ? "book.txt" : p;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleBossKey(event)) return true;

        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);

        InputRedirectMode redirectMode = mode == Mode.FAKE
                ? InputRedirectMode.LEGACY_FAKE_PAGE
                : mode == Mode.TIMESTAMP_NOTE && noteNovelInputActive
                ? InputRedirectMode.NOTE_NOVEL : InputRedirectMode.NONE;
        if (redirectMode != InputRedirectMode.NONE && editor != null && editor.hasFocus()) {
            int key = event.getKeyCode();
            Log.d(TAG, "Fake key code=" + key + " printing=" + event.isPrintingKey()
                    + " repeat=" + event.getRepeatCount()
                    + " ctrl=" + event.isCtrlPressed() + " shift=" + event.isShiftPressed());
            // Real editor/navigation keys that never consume book progress.
            if (key == KeyEvent.KEYCODE_ENTER) return super.dispatchKeyEvent(event);
            // A space is deliberate layout in the fake note. It must neither
            // advance the book nor be replaced with the next book character.
            if (key == KeyEvent.KEYCODE_SPACE) return super.dispatchKeyEvent(event);
            if (key == KeyEvent.KEYCODE_TAB) {
                if (event.getRepeatCount() == 0) {
                    if (event.isShiftPressed()) outdent(); else insertText("\t");
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_DEL || key == KeyEvent.KEYCODE_FORWARD_DEL ||
                    key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN ||
                    key == KeyEvent.KEYCODE_MOVE_HOME || key == KeyEvent.KEYCODE_MOVE_END ||
                    key == KeyEvent.KEYCODE_PAGE_UP || key == KeyEvent.KEYCODE_PAGE_DOWN ||
                    key == KeyEvent.KEYCODE_ESCAPE) {
                return super.dispatchKeyEvent(event);
            }
            if (event.isCtrlPressed() || event.isAltPressed() || event.isMetaPressed()) {
                return super.dispatchKeyEvent(event);
            }
            // Android sends repeated ACTION_DOWN events while a hardware key is
            // held. Consume every repeat so auto-repeat emits the book stream
            // instead of leaking the held key's original value into the note.
            if (event.isPrintingKey()) {
                if (redirectMode == InputRedirectMode.NOTE_NOVEL
                        && !NovelInputPolicy.shouldRedirect(event.getUnicodeChar())) {
                    return super.dispatchKeyEvent(event);
                }
                if (!emitNextBookChar() && redirectMode == InputRedirectMode.NOTE_NOVEL) {
                    stopNoteNovelInputAtBookEnd(true);
                    return super.dispatchKeyEvent(event);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        return handleBossKey(event) || super.dispatchKeyShortcutEvent(event);
    }

    private boolean handleBossKey(KeyEvent event) {
        int key = event.getKeyCode();
        boolean enter = key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean shortcut = key == KeyEvent.KEYCODE_F8
                || (enter && event.isAltPressed())
                || (key == KeyEvent.KEYCODE_B && event.isCtrlPressed() && event.isShiftPressed());
        if (!shortcut) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) toggleBossMode();
        Log.i(TAG, "Boss shortcut received key=" + key + " meta=" + event.getMetaState());
        return true;
    }

    private void toggleRealTranscriptEditing() {
        if (mode != Mode.REAL || editor == null) return;
        if (!editingRealTranscript) {
            if (asrEnabled || RealtimeTranscriptionService.isActive()) {
                toast("请先停止实时转写，再编辑转写内容");
                return;
            }
            realText = storage.realNote();
            realEditBase = realText;
            editingRealTranscript = true;
            editor.setText(realText);
            editor.setSelection(Math.min(realSel, editor.length()));
            updateImeMode();
            editor.requestFocus();
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        } else {
            saveCurrentNow();
            editingRealTranscript = false;
            updateImeMode();
            renderMixedTranscript(true);
        }
        refreshStatus();
    }

    private void confirmNewLesson() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("开始新课")
                .setMessage("上一节课的录音、转写和笔记会保留在课程记录中。"
                        + "开始新课将停止当前录音/转写，并清空当前转写和笔记。")
                .setPositiveButton("开始新课", (dialog, which) -> startNewLesson())
                .setNegativeButton("取消", null)
                .show();
    }

    private void startNewLesson() {
        noteNovelInputActive = false;
        cancelPendingLmStatuses();
        saveHandler.removeCallbacks(saveRunnable);
        if (viewLoaded) saveCurrentNow();
        storage.snapshotSession(fakeText, realText, timestampNoteText, bookPos,
                activeBookChapter());

        if (asrEnabled || RealtimeTranscriptionService.isActive()) stopRealtimeTranscription();
        if (recording || RecordingService.isActive()) {
            Intent stop = new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_STOP);
            startService(stop);
        }
        asrEnabled = false;
        recording = false;
        storage.setRecording(false);
        partial = "";
        asrState = "";
        editingRealTranscript = false;

        storage.beginNewSession();
        lmContext.reset(storage.currentSessionId());
        realText = "";
        realEditBase = "";
        mixedDisplayText = "";
        mixedProcessedLines = 0;
        mixedEligibleRanges = "";
        bossText = "";
        realSel = 0;
        realScroll = 0;
        storage.setRealNote("");
        storage.setDraftNote("");
        storage.setRefinedNote("");
        storage.setRealSelection(0);
        storage.setRealScroll(0);
        clearTimestampNoteForNewLesson();
        storage.setMixedPolicy(0, "");
        mixedNovelStart = bookPos;
        storage.setMixedNovelStart(mixedNovelStart);
        storage.setMixedNovelEnd(mixedNovelStart);
        storage.snapshotSession(fakeText, realText, timestampNoteText, bookPos,
                activeBookChapter());

        // The old editor still displays the previous lesson. showMode() must
        // not save that stale view into the newly cleared session.
        viewLoaded = false;
        resetToSafePage(false);
        toast("新课已开始；点“录音”或“转写”即可重新识别");
    }

    private boolean emitNextBookChar() {
        if (bookText.isEmpty()) { toast("先导入 TXT 或 EPUB"); return false; }
        if (bookPos >= bookText.length()) { toast("已到书末"); return false; }
        int codePoint = bookText.codePointAt(bookPos);
        String s = new String(Character.toChars(codePoint));
        bookPos += Character.charCount(codePoint);
        insertingNovelText = true;
        try { insertText(s); }
        finally { insertingNovelText = false; }
        scheduleActiveBookProgressSave();
        refreshStatus();
        return true;
    }

    private void stopNoteNovelInputAtBookEnd(boolean restartIme) {
        if (!noteNovelInputActive) return;
        noteNovelInputActive = false;
        if (restartIme) updateImeMode();
        refreshStatus();
    }

    private void outdent() {
        Editable e = editor.getText();
        int start = Math.max(0, editor.getSelectionStart());
        int lineStart = start;
        while (lineStart > 0 && e.charAt(lineStart - 1) != '\n') lineStart--;
        int remove = 0;
        if (lineStart < e.length() && e.charAt(lineStart) == '\t') remove = 1;
        else while (remove < 4 && lineStart + remove < e.length() && e.charAt(lineStart + remove) == ' ') remove++;
        if (remove > 0) e.delete(lineStart, lineStart + remove);
    }

    private void insertText(String s) {
        Editable e = editor.getText();
        int a = Math.max(0, editor.getSelectionStart());
        int b = Math.max(0, editor.getSelectionEnd());
        int start = Math.min(a,b), end = Math.max(a,b);
        e.replace(start, end, s);
        editor.setSelection(Math.min(start + s.length(), e.length()));
    }

    private void toggleBossMode() {
        if (mode == Mode.FILE_PREVIEW) {
            resetToSafePage(false);
            return;
        }
        PageNavigationPolicy.Page visiblePage = pageForMode(mode);
        if (visiblePage == null) {
            resetToSafePage(false);
            return;
        }
        if (pageNavigation.currentPage() != visiblePage) pageNavigation.select(visiblePage);
        pageNavigation.toggleBoss();
        PageNavigationPolicy.Page target = pageNavigation.currentPage();
        if (target == PageNavigationPolicy.Page.TRANSCRIPT) {
            if (mode != Mode.BOSS) showBossImmediately();
        } else {
            showNavigationPage(target, true);
        }
    }

    private void showMoreMenu() {
        PopupMenu menu = new PopupMenu(this, mainPageView.moreButton());
        menu.getMenu().add("进度").setOnMenuItemClickListener(item -> {
            startActivity(new Intent(this, BookshelfActivity.class)); return true;
        });
        menu.getMenu().add("导出当前转写").setOnMenuItemClickListener(item -> {
            exportCurrentTranscriptPreview(); return true;
        });
        menu.show();
    }

    private void selectMainPage(PageNavigationPolicy.Page page, boolean announce) {
        pageNavigation.select(page);
        showNavigationPage(pageNavigation.currentPage(), announce);
    }

    private void resetToSafePage(boolean announce) {
        pageNavigation.resetToSafePage();
        showNavigationPage(PageNavigationPolicy.Page.TRANSCRIPT, announce);
    }

    private void showNavigationPage(PageNavigationPolicy.Page page, boolean announce) {
        if (page == PageNavigationPolicy.Page.DUAL_TRANSCRIPT) showMode(Mode.REAL, announce);
        else if (page == PageNavigationPolicy.Page.TIMESTAMP_NOTE) showMode(Mode.TIMESTAMP_NOTE, announce);
        else showMode(Mode.BOSS, announce);
    }

    private PageNavigationPolicy.Page pageForMode(Mode value) {
        if (value == Mode.FAKE || value == Mode.REAL) return PageNavigationPolicy.Page.DUAL_TRANSCRIPT;
        if (value == Mode.TIMESTAMP_NOTE) return PageNavigationPolicy.Page.TIMESTAMP_NOTE;
        if (value == Mode.BOSS) return PageNavigationPolicy.Page.TRANSCRIPT;
        return null;
    }

    /**
     * Intentionally bypasses saveCurrentNow(): that method snapshots multiple
     * large files with fsync and used to block the UI before the safe page was
     * even drawn. Persistence is already debounced and is allowed to finish
     * after the screen has changed.
     */
    private void showBossImmediately() {
        // Capture editable fake text in memory, then persist it off the UI
        // thread. The boss page must never wait for AtomicFile#sync().
        boolean editedReal = captureCurrentInMemory();
        leaveTimestampNotePreview();
        mode = Mode.BOSS;
        title.setText("实时转写");
        if (editor != null) {
            editor.setText(bossText);
            editor.setSelection(editor.length());
        }
        viewLoaded = true;
        updateImeMode();
        if (editor != null) editor.post(() -> {
            android.text.Layout layout = editor.getLayout();
            if (layout != null) editor.scrollTo(0, Math.max(0, layout.getHeight() - editor.getHeight()));
        });
        refreshStatus();
        persistCapturedStateAsync(editedReal);
        toast("实时转写显示");
    }

    private boolean captureCurrentInMemory() {
        if (editor == null) return false;
        if (mode == Mode.FAKE) {
            fakeText = editor.getText().toString();
            fakeSel = Math.max(0, editor.getSelectionStart());
            fakeScroll = Math.max(0, editor.getScrollY());
        } else if (mode == Mode.REAL && editingRealTranscript) {
            realText = editor.getText().toString();
            realSel = Math.max(0, editor.getSelectionStart());
            realScroll = Math.max(0, editor.getScrollY());
            bossText = boundedBossText(bossDisplayText(realText));
            editingRealTranscript = false;
            return true;
        } else if (mode == Mode.TIMESTAMP_NOTE) {
            timestampNoteText = editor.getText().toString();
            timestampNoteSel = Math.max(0, editor.getSelectionStart());
            timestampNoteScroll = Math.max(0, editor.getScrollY());
            timestampNoteRevision++;
        }
        return false;
    }

    private void persistCapturedStateAsync(boolean saveEditedReal) {
        final String savedFake = fakeText;
        final String savedReal = realText;
        final String savedRealBase = realEditBase;
        final int savedFakeSel = fakeSel;
        final int savedFakeScroll = fakeScroll;
        final String savedTimestampNote = timestampNoteText;
        final int savedTimestampNoteSel = timestampNoteSel;
        final int savedTimestampNoteScroll = timestampNoteScroll;
        final long savedTimestampNoteRevision = timestampNoteRevision;
        final int savedPosition = bookPos;
        final int savedChapter = activeBookChapter();
        final String savedBookId = activeBook == null ? "" : activeBook.getId();
        final String savedSessionId = storage.currentSessionId();
        new Thread(() -> {
            storage.setFakeNote(savedFake);
            storage.setFakeSelection(savedFakeSel);
            storage.setFakeScroll(savedFakeScroll);
            String sessionReal = savedReal;
            if (saveEditedReal) {
                sessionReal = storage.saveEditedRealNoteIfCurrent(savedSessionId, savedRealBase, savedReal);
                if (sessionReal == null) return;
            }
            persistTimestampNoteIfCurrent(savedTimestampNote, savedTimestampNoteSel,
                    savedTimestampNoteScroll, savedTimestampNoteRevision);
            if (!savedBookId.isEmpty()) try {
                bookshelf.updateProgress(savedBookId, savedPosition, savedChapter);
            } catch (Exception failure) {
                Log.e(TAG, "Unable to persist captured bookshelf progress", failure);
            }
            BookRecord selected = bookshelf.current();
            if (savedBookId.isEmpty() || (selected != null && savedBookId.equals(selected.getId()))) {
                storage.setBookPos(savedPosition);
                storage.setBookChapter(savedChapter);
            }
            storage.snapshotSessionIfCurrent(savedSessionId, savedFake, sessionReal,
                    savedTimestampNote, savedPosition, savedChapter);
        }, "boss-switch-save").start();
    }

    private void showMode(Mode next, boolean announce) {
        if (viewLoaded) saveCurrentNow();
        leaveTimestampNotePreview();
        if (next != Mode.REAL) editingRealTranscript = false;
        mode = next;
        if (mode == Mode.FAKE) {
            editor.setText(fakeText);
            editor.setSelection(Math.min(fakeSel, editor.length()));
            title.setText("会议记录");
        } else if (mode == Mode.REAL) {
            renderMixedTranscript(true);
            title.setText("实时转写");
        } else if (mode == Mode.TIMESTAMP_NOTE) {
            // A reply from a request started by the previous Activity may have
            // been committed while this instance was on the safe page.
            if (timestampNoteRevision == 0) timestampNoteText = storage.timestampNote();
            setEditorTextProgrammatically(timestampNoteText);
            editor.setSelection(Math.min(timestampNoteSel, editor.length()));
            title.setText("笔记");
        } else if (mode == Mode.BOSS) {
            editor.setText(bossText);
            editor.setSelection(editor.length());
            title.setText("实时转写");
        } else {
            editor.setText(filePreviewText);
            editor.setSelection(editor.length());
            title.setText("文件转写预览");
        }
        viewLoaded = true;
        if (mode == Mode.FAKE || mode == Mode.REAL) {
            storage.setRealMode(mode == Mode.REAL);
        }
        updateImeMode();
        final int targetScroll = mode == Mode.FAKE ? fakeScroll
                : (mode == Mode.TIMESTAMP_NOTE ? timestampNoteScroll
                : (mode == Mode.REAL || mode == Mode.BOSS ? realScroll : Integer.MAX_VALUE));
        editor.post(() -> editor.scrollTo(0, targetScroll));
        refreshStatus();
        if (announce) toast(mode == Mode.FAKE ? "会议记录模式" : (mode == Mode.REAL ? "会议记录" :
                (mode == Mode.TIMESTAMP_NOTE ? "笔记" : (mode == Mode.BOSS ? "实时转写显示" : "文件转写预览"))));
    }

    private void setEditorTextProgrammatically(String text) {
        formattingTimestampNote = true;
        try {
            editor.setText(text == null ? "" : text);
        } finally {
            formattingTimestampNote = false;
        }
    }

    private void toggleTimestampNotePreview() {
        if (mode != Mode.TIMESTAMP_NOTE || editor == null || notePreviewView == null) return;
        if (timestampNotePreview) {
            timestampNotePreview = false;
            notePreviewView.setVisible(false);
            editor.setVisibility(View.VISIBLE);
            setEditorTextProgrammatically(timestampNoteText);
            editor.setSelection(Math.min(timestampNoteSel, editor.length()));
            editor.scrollTo(0, timestampNoteScroll);
            editor.requestFocus();
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        } else {
            saveCurrentNow();
            timestampNotePreview = true;
            refreshTimestampNotePreview();
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
            editor.clearFocus();
            editor.setVisibility(View.GONE);
            notePreviewView.setVisible(true);
        }
        updateImeMode();
        refreshStatus();
    }

    private void leaveTimestampNotePreview() {
        timestampNotePreview = false;
        if (notePreviewView != null) notePreviewView.setVisible(false);
        if (editor != null) editor.setVisibility(View.VISIBLE);
    }

    private void refreshTimestampNotePreviewIfVisible() {
        if (timestampNotePreview && mode == Mode.TIMESTAMP_NOTE) refreshTimestampNotePreview();
    }

    private void refreshTimestampNotePreview() {
        if (notePreviewView != null) notePreviewView.render(storage.currentSessionId(), timestampNoteText);
    }

    private void updateImeMode() {
        if (editor == null) return;
        InputRedirectMode redirectMode = mode == Mode.FAKE
                ? InputRedirectMode.LEGACY_FAKE_PAGE
                : mode == Mode.TIMESTAMP_NOTE && noteNovelInputActive && !timestampNotePreview
                ? InputRedirectMode.NOTE_NOVEL : InputRedirectMode.NONE;
        editor.setInputRedirectMode(redirectMode);
        if (timestampNotePreview && mode == Mode.TIMESTAMP_NOTE) {
            editor.setVisibility(View.GONE);
            if (notePreviewView != null) notePreviewView.setVisible(true);
            return;
        }
        // Mixed REAL text is display-only. Explicit edit mode swaps in the
        // canonical transcript so novel lines can never be saved by mistake.
        boolean preview = mode == Mode.FILE_PREVIEW || mode == Mode.BOSS
                || (mode == Mode.REAL && !editingRealTranscript);
        editor.setFocusable(!preview);
        editor.setFocusableInTouchMode(!preview);
        editor.setCursorVisible(!preview);
        if (Build.VERSION.SDK_INT >= 21) {
            editor.setShowSoftInputOnFocus(mode == Mode.TIMESTAMP_NOTE || (mode == Mode.REAL && editingRealTranscript));
        }
        if (mode == Mode.FAKE) {
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        }
    }

    private void toggleRecording() {
        if (recording) {
            if (asrEnabled) {
                asrEnabled = false;
                stopRealtimeTranscription();
                partial = "";
            }
            Intent stop = new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_STOP);
            startService(stop);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        startRecordingService();
    }

    private void startRecordingService() {
        Intent start = new Intent(this, RecordingService.class).setAction(RecordingService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(start); else startService(start);
        recording = true;
        recordingSummary = "";
        if (asrEngine.isAvailable() && !asrEnabled) {
            asrEnabled = true;
            asrState = "正在加载本地中文模型";
            startRealtimeTranscription();
        }
        refreshStatus();
        if (!asrEngine.isAvailable()) {
            toast("录音已开始；本机没有可用的系统转写服务");
        }
    }

    private void startRealtimeTranscription() {
        Intent start = new Intent(this, RealtimeTranscriptionService.class)
                .setAction(RealtimeTranscriptionService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(start); else startService(start);
    }

    private void stopRealtimeTranscription() {
        Intent stop = new Intent(this, RealtimeTranscriptionService.class)
                .setAction(RealtimeTranscriptionService.ACTION_STOP);
        startService(stop);
    }

    @Override public void onRequestPermissionsResult(int req, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(req, permissions, grants);
        if (req == REQ_AUDIO && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startRecordingService();
    }

    private void toggleAsr() {
        if (asrEnabled) {
            asrEnabled = false;
            stopRealtimeTranscription();
            partial = "";
            refreshStatus();
            return;
        }
        if (!asrEngine.isAvailable()) {
            asrState = asrEngine.unavailableReason();
            toast(asrState);
            refreshStatus();
            return;
        }
        asrEnabled = true;
        asrState = "正在启动转写";
        startRealtimeTranscription();
        refreshStatus();
    }

    private void toggleAsrMode() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override public void onPartial(String text) {
        partial = text == null ? "" : text;
        refreshStatus();
    }

    @Override public void onFinal(String text) {
        partial = "";
        if (text == null || text.trim().isEmpty()) return;
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        if (mode == Mode.REAL) saveCurrentNow();
        realText = storage.realNote();
        if (!realText.isEmpty() && !realText.endsWith("\n")) realText += "\n";
        String stampedLine = "[" + stamp + "] " + text.trim();
        realText += stampedLine + "\n";
        storage.setRealNote(realText);
        recordNewMixedLines();
        appendBossLine(stampedLine);
        if (mode == Mode.REAL) {
            renderMixedTranscript(false);
        } else if (mode == Mode.BOSS) {
            renderBossAppend();
        }
        refreshStatus();
    }

    @Override public void onState(String text) {
        asrState = text == null ? "" : text;
        if (asrState.contains("不可用") || asrState.contains("失败")) asrEnabled = false;
        refreshStatus();
    }

    private void refreshStatus() {
        updateKeepScreenOn();
        if (status == null) return;
        double pct = bookText.isEmpty() ? 0.0 : (100.0 * bookPos / Math.max(1, bookText.length()));
        StringBuilder s = new StringBuilder();
        if (mode == Mode.FAKE) {
            s.append("旧版草稿页");
        } else if (mode == Mode.TIMESTAMP_NOTE) {
            s.append(timestampNotePreview ? "笔记页 · Markdown 预览"
                    : noteNovelInputActive ? "笔记页 · 假装输入中 · 输入 /// 结束"
                    : "笔记页 · 换行自动加入时间戳");
        } else if (mode == Mode.BOSS) {
            s.append("实时转写页 · 单线输出 · 本地自动保存");
        } else {
            s.append(editingRealTranscript
                    ? "实时转写页 · 正在编辑原始转写"
                    : "实时转写页 · 双线输出 · 本地自动保存");
        }
        if (recording) s.append("   ● REC");
        if (asrEnabled) s.append("   ").append(asrState.isEmpty() ? "ASR" : asrState);
        else if (!asrEngine.isAvailable()) s.append("   本地转写不可用");
        else if (mode == Mode.REAL) s.append("   ").append(transcriptionModeLabel());
        if (!recording && !recordingSummary.isEmpty()) s.append("   ").append(recordingSummary);
        if (!partial.isEmpty() && (mode == Mode.REAL || mode == Mode.TIMESTAMP_NOTE)) s.append("   正在识别：").append(partial);
        if (fileTranscribing || !fileAsrStatus.isEmpty()) s.append("   ").append(fileAsrStatus);
        status.setText(s.toString());
        recordButton.setText(recording ? "停止" : "录音");
        asrButton.setText(asrEnabled ? "停转写" : "转写");
        if (editButton != null) {
            editButton.setVisibility(mode == Mode.REAL ? View.VISIBLE : View.GONE);
            editButton.setText(editingRealTranscript ? "完成" : "编辑");
        }
        if (notePreviewButton != null) {
            notePreviewButton.setVisibility(mode == Mode.TIMESTAMP_NOTE ? View.VISIBLE : View.GONE);
            notePreviewButton.setText(timestampNotePreview ? "编辑" : "预览");
        }
        boolean noteEditing = mode == Mode.TIMESTAMP_NOTE && !timestampNotePreview;
        if (notePhotoButton != null) notePhotoButton.setVisibility(noteEditing ? View.VISIBLE : View.GONE);
        if (noteImageButton != null) noteImageButton.setVisibility(noteEditing ? View.VISIBLE : View.GONE);
        if (mainPageView != null)
            mainPageView.setPageActionsVisible(mode == Mode.REAL || mode == Mode.TIMESTAMP_NOTE);
        if (mainPageView != null)
            mainPageView.setActivePage(mode == Mode.TIMESTAMP_NOTE ? 1 : 0);
        if (mainPageView != null)
            mainPageView.setOutputMode(mode == Mode.BOSS ? 0 : mode == Mode.REAL ? 1 : -1);
        if (fileAsrButton != null) {
            fileAsrButton.setText(fileTranscribing ? "暂停文件"
                    : (fileCheckpoint != null && fileCheckpoint.canResume() ? "继续文件" : "文件转"));
        }
    }

    private String readableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void openLastRecording() {
        String raw = storage.publicRecordingUri();
        if (raw == null || raw.isEmpty()) {
            toast("还没有已发布的录音");
            return;
        }
        try {
            Uri uri = Uri.parse(raw);
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, "audio/mp4");
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(view);
        } catch (Throwable e) {
            toast("录音位于 音乐/NoteShadow");
        }
    }

    private void scheduleSave() {
        saveHandler.removeCallbacks(saveRunnable);
        saveHandler.postDelayed(saveRunnable, 350);
    }
    private final Runnable saveRunnable = this::saveCurrentNow;

    private void initializeMixedPolicy() {
        int currentLines = MixedTranscriptPresenter.lineCount(realText);
        if (storage.mixedPolicyInitialized()) {
            mixedEligibleRanges = MixedTranscriptPresenter.clipEligibleRanges(
                    storage.mixedEligibleRanges(), currentLines);
        } else {
            // Preserve the pre-upgrade mixed page. Only future lines are gated
            // by whether that page is actually visible in the foreground.
            mixedEligibleRanges = currentLines == 0 ? "" : "1-" + currentLines;
        }
        // Any lines produced while the activity was not alive are background
        // lines and must never retroactively advance the novel.
        mixedProcessedLines = currentLines;
        storage.setMixedPolicy(mixedProcessedLines, mixedEligibleRanges);
    }

    private void recordNewMixedLines() {
        int currentLines = MixedTranscriptPresenter.lineCount(realText);
        if (currentLines < mixedProcessedLines) {
            mixedEligibleRanges = MixedTranscriptPresenter.clipEligibleRanges(
                    mixedEligibleRanges, currentLines);
        } else if (currentLines > mixedProcessedLines
                && activityForeground && mode == Mode.REAL && !editingRealTranscript) {
            mixedEligibleRanges = MixedTranscriptPresenter.appendEligibleRange(
                    mixedEligibleRanges, mixedProcessedLines + 1, currentLines);
        }
        mixedProcessedLines = currentLines;
        storage.setMixedPolicy(mixedProcessedLines, mixedEligibleRanges);
    }

    private void saveCurrentNow() {
        if (editor == null) return;
        if (mode == Mode.FILE_PREVIEW || mode == Mode.BOSS) return;
        String t = editor.getText().toString();
        int sel = Math.max(0, editor.getSelectionStart());
        if (mode == Mode.FAKE) {
            fakeText = t; fakeSel = sel; fakeScroll = Math.max(0, editor.getScrollY());
            storage.setFakeNote(t); storage.setFakeSelection(sel); storage.setFakeScroll(fakeScroll);
        } else if (mode == Mode.REAL) {
            if (editingRealTranscript) {
                String latest = storage.realNote();
                if (!latest.equals(realEditBase) && latest.startsWith(realEditBase)) {
                    String appended = latest.substring(realEditBase.length());
                    if (!t.isEmpty() && !t.endsWith("\n") && !appended.isEmpty()) t += "\n";
                    t += appended;
                }
                realText = t;
                realEditBase = t;
                realSel = Math.min(sel, t.length());
                storage.setRealNote(t);
                storage.setRealSelection(realSel);
                recordNewMixedLines();
            }
            realScroll = Math.max(0, editor.getScrollY());
            storage.setRealScroll(realScroll);
        } else if (mode == Mode.TIMESTAMP_NOTE) {
            timestampNoteText = t;
            timestampNoteSel = Math.min(sel, t.length());
            timestampNoteScroll = Math.max(0, editor.getScrollY());
            long revision = ++timestampNoteRevision;
            persistTimestampNoteIfCurrent(timestampNoteText, timestampNoteSel,
                    timestampNoteScroll, revision);
            saveHandler.removeCallbacks(bookProgressSaveRunnable);
            persistActiveBookProgress();
            storage.snapshotSession(fakeText, realText, timestampNoteText, bookPos,
                    activeBookChapter());
            return;
        }
        saveHandler.removeCallbacks(bookProgressSaveRunnable);
        persistActiveBookProgress();
        int chapter = activeBookChapter();
        storage.snapshotSession(fakeText, realText, timestampNoteText, bookPos, chapter);
    }

    private void clearTimestampNoteForNewLesson() {
        synchronized (timestampNoteWriteLock) {
            timestampNoteText = "";
            timestampNoteSel = 0;
            timestampNoteScroll = 0;
            timestampNoteRevision++;
            storage.setTimestampNote("");
            storage.setTimestampNoteSelection(0);
            storage.setTimestampNoteScroll(0);
        }
    }

    private void runNoteCommandFromNewline(Editable note, int newlinePosition) {
        if (mode != Mode.TIMESTAMP_NOTE || newlinePosition < 0 || newlinePosition > note.length()) return;
        if (lmContext.ensureSession(storage.currentSessionId()))
            LmDiagnosticLog.record(this, LmDiagnosticLog.Event.CONTEXT_RESET, "COURSE");
        int lineStart = newlinePosition == 0 ? 0 : note.toString().lastIndexOf('\n', newlinePosition - 1) + 1;
        String line = note.subSequence(lineStart, newlinePosition).toString();
        LmNoteCommand.Parsed command = LmNoteCommand.parse(line);
        NovelInputPolicy.Command novelCommand = NovelInputPolicy.parseCommand(line);
        LmContextStore.Mode inputMode = lmContext.mode();
        // Reset must always be available, including while an unfinished block is open.
        if (command.kind() == LmNoteCommand.Kind.NEW_CONTEXT) {
            LmDiagnosticLog.record(this, LmDiagnosticLog.Event.CONTEXT_RESET, "LMNEW");
            cancelPendingLmStatuses();
            if (viewLoaded) saveCurrentNow();
            lmContext.reset(storage.currentSessionId(), LmContextStore.Mode.NORMAL);
            toast("当前课程的模型对话已重新开始");
            return;
        }
        if (inputMode == LmContextStore.Mode.BLOCK
                && command.kind() != LmNoteCommand.Kind.BLOCK_END) {
            if (command.kind() != LmNoteCommand.Kind.NONE)
                LmDiagnosticLog.record(this, LmDiagnosticLog.Event.NOTE_BLOCK_LITERAL, "BLOCK");
            return;
        }
        if (novelCommand == NovelInputPolicy.Command.END && noteNovelInputActive) {
            noteNovelInputActive = false;
            updateImeMode();
            refreshStatus();
            toast("假装输入已结束");
            return;
        }
        if (novelCommand == NovelInputPolicy.Command.START) {
            if (inputMode != LmContextStore.Mode.NORMAL) {
                toast("请先输入 /// 结束连续模型对话");
            } else if (bookText.isEmpty()) {
                toast("请先在进度中选择 TXT 或 EPUB 文档");
            } else if (bookPos >= bookText.length()) {
                toast("已到书末，请先调整阅读进度");
            } else {
                noteNovelInputActive = true;
                updateImeMode();
                refreshStatus();
                toast("假装输入已开始；输入 /// 结束");
            }
            return;
        }
        ArcNoteCommand.Parsed arc = ArcNoteCommand.parse(line);
        if (arc.recognized()) {
            insertArcExcerpt(note, newlinePosition, arc);
            return;
        }
        if (command.kind() == LmNoteCommand.Kind.BLOCK_BEGIN) {
            LmDiagnosticLog.record(this, LmDiagnosticLog.Event.NOTE_WAITING, "BLOCK");
            lmContext.setMode(LmContextStore.Mode.BLOCK);
            toast("多行提问开始；输入 /lmled 提交");
            return;
        }
        if (command.kind() == LmNoteCommand.Kind.CONTINUOUS_BEGIN) {
            LmDiagnosticLog.record(this, LmDiagnosticLog.Event.NOTE_WAITING, "CONTINUOUS");
            lmContext.setMode(LmContextStore.Mode.CONTINUOUS);
            toast("连续对话开始；输入 /// 结束");
            return;
        }
        if (command.kind() == LmNoteCommand.Kind.CONTINUOUS_END) {
            LmDiagnosticLog.record(this, LmDiagnosticLog.Event.NOTE_WAITING, "NORMAL");
            if (inputMode == LmContextStore.Mode.CONTINUOUS) {
                lmContext.setMode(LmContextStore.Mode.NORMAL);
                toast("连续对话已结束");
            }
            return;
        }
        String question;
        if (command.kind() == LmNoteCommand.Kind.BLOCK_END) {
            if (inputMode != LmContextStore.Mode.BLOCK) {
                LmDiagnosticLog.record(this, LmDiagnosticLog.Event.NOTE_REJECTED, "NO_BLOCK");
                toast("请先输入 /lmlbg"); return;
            }
            question = LmBlockPrompt.extract(note.toString(), lineStart);
            lmContext.setMode(LmContextStore.Mode.NORMAL);
        } else if (command.kind() == LmNoteCommand.Kind.SINGLE_PROMPT
                && inputMode != LmContextStore.Mode.BLOCK) {
            question = command.payload();
        } else if (inputMode == LmContextStore.Mode.CONTINUOUS
                && command.kind() == LmNoteCommand.Kind.NONE) {
            question = stripNoteTimestamp(line).trim();
            if (question.isEmpty()) return;
        } else return;
        if (question == null) question = "";
        final String commandType = command.kind() == LmNoteCommand.Kind.BLOCK_END ? "BLOCK"
                : inputMode == LmContextStore.Mode.CONTINUOUS
                && command.kind() == LmNoteCommand.Kind.NONE ? "CONTINUOUS" : "SINGLE";
        LmDiagnosticLog.record(this, LmDiagnosticLog.Event.NOTE_COMMAND, commandType);
        String message;
        boolean shouldCall = false;
        String marker = "";
        if (question.isEmpty() || question.length() > 8_000) {
            message = question.isEmpty() ? "请输入问题文本" : "问题过长（最多 8000 字）";
            LmDiagnosticLog.record(this, LmDiagnosticLog.Event.NOTE_REJECTED,
                    question.isEmpty() ? "EMPTY" : "TOO_LONG");
        } else if (new LmProviderConfigRepository(this).current().apiKey().isEmpty()) {
            message = "尚未配置模型密钥，请打开设置";
            LmDiagnosticLog.record(this, LmDiagnosticLog.Event.NOTE_REJECTED, "NO_KEY");
        } else {
            marker = "正在调用模型…（" + lmRequestIds.incrementAndGet() + "）";
            message = marker;
            shouldCall = true;
        }
        int statusStart = newlinePosition + 1;
        if (statusStart + 11 > note.length()) {
            LmDiagnosticLog.record(this, LmDiagnosticLog.Event.NOTE_REJECTED, "NO_NEWLINE");
            return;
        }
        String timestampPrefix = note.subSequence(statusStart, statusStart + 11).toString();
        if (!timestampPrefix.matches("^\\[\\d{2}:\\d{2}:\\d{2}\\] $")) {
            LmDiagnosticLog.record(this, LmDiagnosticLog.Event.NOTE_REJECTED, "NO_TIMESTAMP");
            toast("请在完整命令行末尾按回车重试");
            return;
        }
        formattingTimestampNote = true;
        try {
            note.insert(statusStart + 11, message + "\n" + timestampPrefix);
            editor.setSelection(statusStart + 11 + message.length() + 1 + timestampPrefix.length());
        } finally { formattingTimestampNote = false; }
        if (!shouldCall) return;
        timestampNoteText = note.toString();
        // Persist the unique marker before the worker starts; the worker can
        // complete safely even if this Activity is recreated meanwhile.
        saveCurrentNow();
        final String courseId = storage.currentSessionId();
        final long generation = lmContext.generation();
        final String prompt = question;
        final String requestMarker = marker;
        LmDiagnosticLog.record(this, LmDiagnosticLog.Event.REQUEST_QUEUED, commandType);
        lmRequests.execute(() -> {
            List<LmConversation.Message> messages = lmContext.requestMessages(courseId, generation, prompt);
            if (messages == null) {
                LmDiagnosticLog.record(getApplicationContext(), LmDiagnosticLog.Event.REQUEST_CANCELLED, "GENERATION");
                return;
            }
            long started = System.currentTimeMillis();
            LmDiagnosticLog.record(getApplicationContext(), LmDiagnosticLog.Event.REQUEST_STARTED, commandType);
            String answer;
            boolean success = false;
            try {
                answer = new LmChatClient().answer(getApplicationContext(), messages);
                success = true;
                LmDiagnosticLog.record(getApplicationContext(), LmDiagnosticLog.Event.REQUEST_SUCCESS,
                        durationCode(started));
            }
            catch (Exception failure) {
                answer = "调用失败：" + safeDsError(failure);
                LmDiagnosticLog.record(getApplicationContext(), LmDiagnosticLog.Event.REQUEST_FAILURE,
                        LmFailureCategory.of(failure));
            }
            final String response = answer;
            final boolean completed = success;
            if (!lmContext.isCurrent(courseId, generation)) {
                LmDiagnosticLog.record(getApplicationContext(), LmDiagnosticLog.Event.REQUEST_CANCELLED, "GENERATION");
                return;
            }
            String stamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String replacement = lmAnswerReplacement(response, stamp);
            boolean written = storage.replaceTimestampNoteMarkerIfCurrent(courseId, requestMarker, replacement) != null;
            if (written && completed) lmContext.addSuccess(courseId, generation, prompt, response);
            LmDiagnosticLog.record(getApplicationContext(), written
                    ? LmDiagnosticLog.Event.REPLY_WRITTEN : LmDiagnosticLog.Event.REPLY_SKIPPED,
                    written ? "NOTE" : "MARKER_OR_COURSE");
            runOnUiThread(() -> refreshLmNoteAfterWorker(courseId, requestMarker,
                    generation, prompt, written, completed, response));
        });
    }

    private static String stripNoteTimestamp(String line) {
        return line.replaceFirst("^\\[\\d{2}:\\d{2}:\\d{2}\\] ", "");
    }

    private static String durationCode(long started) {
        long seconds = Math.min(999, Math.max(0, (System.currentTimeMillis() - started) / 1000));
        return "S" + seconds;
    }

    private static String lmAnswerReplacement(String answer, String stamp) {
        return (answer.startsWith("调用失败：") ? "" : "模型回复：")
                + TimestampNoteFormatter.formatInsertedText("\n" + answer.trim(), stamp);
    }

    private void refreshLmNoteAfterWorker(String courseId, String marker, long generation,
                                          String prompt, boolean written, boolean completed,
                                          String answer) {
        if (!courseId.equals(storage.currentSessionId())) return;
        // A delayed editor save may have restored the marker after the worker
        // wrote its reply. Replace the marker in the live editor as well, so
        // that save cannot overwrite the reply or discard edits made while waiting.
        if (mode == Mode.TIMESTAMP_NOTE && editor != null
                && editor.getText().toString().contains(marker)
                && (written || lmContext.isCurrent(courseId, generation))
                && appendDsAnswer(courseId, marker, answer)) {
            if (!written) {
                if (completed) lmContext.addSuccess(courseId, generation, prompt, answer);
                LmDiagnosticLog.record(this, LmDiagnosticLog.Event.REPLY_WRITTEN, "UI_RECOVERY");
            }
            return;
        }
        if (!written) return;
        timestampNoteText = storage.timestampNote();
        timestampNoteRevision++;
        if (mode == Mode.TIMESTAMP_NOTE && editor != null) {
            int cursor = Math.min(Math.max(0, editor.getSelectionStart()), timestampNoteText.length());
            setEditorTextProgrammatically(timestampNoteText);
            editor.setSelection(cursor);
        }
        refreshTimestampNotePreviewIfVisible();
        toast(answer.startsWith("调用失败：") ? "模型调用失败" : "模型回答已加入课堂笔记");
    }

    private void insertArcExcerpt(Editable note, int newlinePosition, ArcNoteCommand.Parsed arc) {
        int at = newlinePosition + 1 + 11;
        if (at > note.length()) return;
        String prefix = note.subSequence(at - 11, at).toString();
        if (!prefix.matches("^\\[\\d{2}:\\d{2}:\\d{2}\\] $")) return;
        String insertion;
        if (!arc.valid()) {
            insertion = "摘录失败：" + arc.error();
        } else {
            String courseId = storage.currentSessionId();
            List<String> lines = ArcTranscriptExcerpt.latest(storage.realNote(), arc.count());
            if (!courseId.equals(storage.currentSessionId())) return;
            if (lines.isEmpty()) {
                insertion = "暂无可追加的实时转写";
            } else {
                String joined = android.text.TextUtils.join("\n", lines);
                if (joined.length() > 40_000) insertion = "摘录失败：内容过长，请减少行数";
                else insertion = "转写摘录（最近 " + lines.size() + " 行）：\n" + joined;
            }
        }
        String decorated = insertion + "\n" + prefix;
        formattingTimestampNote = true;
        try {
            note.insert(at, decorated);
            editor.setSelection(Math.min(at + decorated.length(), editor.length()));
        } finally { formattingTimestampNote = false; }
        timestampNoteText = note.toString();
        if (viewLoaded) saveCurrentNow();
    }

    private static String safeDsError(Exception failure) {
        String message = failure.getMessage();
        if (message != null && (message.startsWith("密钥无效") || message.startsWith("请求过于频繁")
                || message.startsWith("服务暂不可用") || message.startsWith("账户余额不足")
                || message.startsWith("接口参数不兼容") || message.startsWith("模型没有")
                || message.startsWith("模型回复过长"))) return message;
        return "网络连接或响应异常，请稍后重试";
    }

    private void cancelPendingLmStatuses() {
        String cancelled = "已取消（模型上下文已重置）";
        if (mode == Mode.TIMESTAMP_NOTE && editor != null) {
            Editable active = editor.getText();
            String current = active.toString();
            if (!current.contains("正在调用模型…")) return;
            formattingTimestampNote = true;
            try { active.replace(0, active.length(), current.replaceAll("正在调用模型…（\\d+）", cancelled)); }
            finally { formattingTimestampNote = false; }
            timestampNoteText = active.toString();
        } else if (timestampNoteText.contains("正在调用模型…")) {
            timestampNoteText = timestampNoteText.replaceAll("正在调用模型…（\\d+）", cancelled);
            storage.setTimestampNote(timestampNoteText);
        }
    }

    private boolean appendDsAnswer(String courseId, String marker, String answer) {
        boolean gone = isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed());
        if (!courseId.equals(storage.currentSessionId())) return false;
        String active = mode == Mode.TIMESTAMP_NOTE && editor != null
                ? editor.getText().toString() : timestampNoteText;
        if (!active.contains(marker)) return false;
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String replacement = lmAnswerReplacement(answer, stamp);
        int markerStart = active.indexOf(marker);
        String next = active.substring(0, markerStart) + replacement
                + active.substring(markerStart + marker.length());
        if (gone) {
            return storage.replaceTimestampNoteMarkerIfCurrent(courseId, marker, replacement) != null;
        }
        synchronized (timestampNoteWriteLock) {
            if (!storage.saveTimestampNoteIfCurrent(courseId, fakeText, realText, next,
                    bookPos, activeBookChapter())) return false;
            timestampNoteText = next;
            timestampNoteRevision++;
        }
        if (mode == Mode.TIMESTAMP_NOTE && editor != null) {
            int cursor = Math.max(0, editor.getSelectionStart());
            if (cursor > markerStart) cursor += replacement.length() - marker.length();
            setEditorTextProgrammatically(timestampNoteText);
            editor.setSelection(Math.min(cursor, editor.length()));
        }
        refreshTimestampNotePreviewIfVisible();
        toast(answer.startsWith("调用失败：") ? "模型调用失败" : "模型回答已加入课堂笔记");
        return true;
    }

    private void persistTimestampNoteIfCurrent(String text, int selection, int scroll,
                                               long revision) {
        synchronized (timestampNoteWriteLock) {
            if (revision != timestampNoteRevision) return;
            storage.setTimestampNote(text);
            storage.setTimestampNoteSelection(selection);
            storage.setTimestampNoteScroll(scroll);
        }
    }

    /** Display-only canonical transcription for the boss page; novel lines are never included. */
    private static String bossDisplayText(String source) {
        if (source == null || source.isEmpty()) return "";
        return source.trim();
    }

    private static String boundedBossText(String source) {
        if (source == null || source.isEmpty()) return "";
        if (source.length() <= MAX_BOSS_DISPLAY_CHARS) return source;
        int start = source.length() - MAX_BOSS_DISPLAY_CHARS;
        int nextLine = source.indexOf('\n', start);
        if (nextLine >= 0 && nextLine + 1 < source.length()) start = nextLine + 1;
        return source.substring(start);
    }

    private void appendBossLine(String line) {
        if (line == null) return;
        String clean = bossDisplayText(line);
        if (clean.isEmpty()) return;
        if (!bossText.isEmpty()) bossText += "\n";
        bossText = boundedBossText(bossText + clean);
    }

    /** Only appends the just-produced safe text; it never rebuilds a full page. */
    private void renderBossAppend() {
        if (editor == null) return;
        String shown = editor.getText().toString();
        if (bossText.startsWith(shown)) editor.append(bossText.substring(shown.length()));
        else if (!shown.equals(bossText)) editor.setText(bossText);
    }

    /** Updates the mixed foreground page without replacing the whole EditText on every ASR result. */
    private void renderMixedTranscript(boolean force) {
        if (editingRealTranscript) return;
        MixedTranscriptPresenter.BuildResult result = MixedTranscriptPresenter.buildResult(
                realText, bookText, mixedNovelStart, mixedEligibleRanges);
        String next = result.text;
        updateBookProgressFromMixed(result.nextNovelPosition);
        if (editor == null) {
            mixedDisplayText = next;
            return;
        }
        String shown = editor.getText().toString();
        if (!force && next.startsWith(shown)) editor.append(next.substring(shown.length()));
        else if (!shown.equals(next)) editor.setText(next);
        mixedDisplayText = next;
        editor.setSelection(editor.length());
        editor.post(() -> {
            android.text.Layout layout = editor.getLayout();
            if (layout != null) editor.scrollTo(0, Math.max(0, layout.getHeight() - editor.getHeight()));
        });
    }

    private void applyMixedResult(MixedTranscriptPresenter.BuildResult result) {
        mixedDisplayText = result.text;
        updateBookProgressFromMixed(result.nextNovelPosition);
    }

    private void updateBookProgressFromMixed(int position) {
        int next = BookProgress.clampToCodePointBoundary(bookText, position);
        int previousMixedEnd = BookProgress.clampToCodePointBoundary(bookText,
                storage.mixedNovelEndInitialized() ? storage.mixedNovelEnd() : mixedNovelStart);
        storage.setMixedNovelEnd(next);
        // Manual fake-page typing also advances bookPos. Never move that independent
        // progress backward merely because the mixed page is being rebuilt.
        if (bookPos != previousMixedEnd || next == bookPos) return;
        bookPos = next;
        scheduleActiveBookProgressSave();
        refreshStatus();
    }

    private void showFilePreview() {
        if (filePreviewText.isEmpty()) {
            toast("还没有文件转写内容");
            return;
        }
        showMode(Mode.FILE_PREVIEW, false);
    }

    private void appendFilePreview(String value) {
        if (value == null || value.isEmpty()) return;
        filePreviewText += value;
        boolean trimmed = false;
        if (filePreviewText.length() > MAX_FILE_PREVIEW_CHARS) {
            int keepFrom = filePreviewText.indexOf('\n', filePreviewText.length() - MAX_FILE_PREVIEW_CHARS);
            if (keepFrom < 0) keepFrom = filePreviewText.length() - MAX_FILE_PREVIEW_CHARS;
            filePreviewText = "……前面的预览内容已省略，完整内容见导出的 TXT……\n" + filePreviewText.substring(keepFrom + 1);
            trimmed = true;
        }
        if (mode == Mode.FILE_PREVIEW && editor != null) {
            // Replacing the entire EditText for every decoded sentence causes a
            // visible blank/re-layout flash on long recordings. Append the new
            // line in place; a full rebuild is needed only after the preview cap.
            if (trimmed) editor.setText(filePreviewText);
            else editor.append(value);
            editor.setSelection(editor.length());
            editor.post(() -> {
                android.text.Layout layout = editor.getLayout();
                if (layout != null) editor.scrollTo(0, Math.max(0, layout.getHeight() - editor.getHeight()));
            });
        }
    }

    @Override protected void onResume() {
        super.onResume();
        reloadSelectedBook();
        keepScreenOnDuringWork = getSharedPreferences(ActiveWorkPolicy.PREFERENCES,
                MODE_PRIVATE).getBoolean(ActiveWorkPolicy.KEEP_SCREEN_ON, true);
        if (!asrEnabled) {
            transcriptionMode = new TranscriptionSettingsRepository(this).mode();
            asrEngine = new LocalSherpaAsr(this, this, transcriptionMode);
        }
        updateKeepScreenOn();
        // Background/lock transitions clear the in-memory boss-key target.
        // The sole exception is a one-shot return from our note media picker.
        boolean restoreNotePage = noteMediaReturn.consumeRestore();
        if (resumeOnSafePage) {
            if (restoreNotePage) selectMainPage(PageNavigationPolicy.Page.TIMESTAMP_NOTE, false);
            else resetToSafePage(false);
        }
        resumeOnSafePage = false;
        activityForeground = true;
    }

    @Override protected void onPause() {
        activityForeground = false;
        saveCurrentNow();
        // The next visible frame after screen-off/backgrounding must be the
        // transcript-only page, never the page that can emit reading text.
        resumeOnSafePage = true;
        super.onPause();
    }
    @Override protected void onDestroy() {
        saveCurrentNow();
        // Both live ASR and file conversion own their application-level work;
        // destroying this page (background, rotation, screen lock) must not
        // cancel either task.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try { unregisterReceiver(recordingReceiver); } catch (Throwable ignored) {}
        try { unregisterReceiver(transcriptionReceiver); } catch (Throwable ignored) {}
        super.onDestroy();
    }


    private void showReadingProgress() {
        double pct = bookText.isEmpty() ? 0.0 : (100.0 * bookPos / Math.max(1, bookText.length()));
        toast(storage.bookName() + "  " + String.format(Locale.getDefault(), "%.3f%%", pct)
                + "  (" + bookPos + "/" + bookText.length() + ")");
    }

    private void validateLastRecording() {
        String path = storage.lastRecording();
        if (path == null || path.isEmpty()) return;
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(path);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    long duration = f.containsKey(MediaFormat.KEY_DURATION) ? f.getLong(MediaFormat.KEY_DURATION) : -1;
                    Log.i(TAG, "Last recording readable mime=" + mime + " durationUs=" + duration);
                    return;
                }
            }
            Log.e(TAG, "Last recording has no audio track");
        } catch (Throwable e) {
            Log.e(TAG, "Last recording is unreadable: " + e.getClass().getSimpleName());
        } finally {
            extractor.release();
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void updateKeepScreenOn() {
        boolean keepOn = ActiveWorkPolicy.shouldKeepScreenOn(keepScreenOnDuringWork,
                recording, asrEnabled, fileTranscribing);
        if (keepOn) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private String transcriptionModeLabel() {
        if (transcriptionMode == LiveTranscriptionMode.QWEN_ONLY) return "仅 Qwen · 省电";
        if (transcriptionMode == LiveTranscriptionMode.SENSEVOICE_FALLBACK) return "兼容模式";
        return "实时草稿 + Qwen";
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private final class ShadowEditText extends EditText {
        private InputRedirectMode inputRedirectMode = InputRedirectMode.NONE;

        ShadowEditText(Context context) { super(context); }

        void setInputRedirectMode(InputRedirectMode value) {
            InputRedirectMode next = value == null ? InputRedirectMode.NONE : value;
            if (inputRedirectMode == next) return;
            inputRedirectMode = next;
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            imm.restartInput(this);
        }

        @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            InputConnection base = super.onCreateInputConnection(outAttrs);
            if (base == null) return null;
            return new InputConnectionWrapper(base, false) {
                @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                    if (inputRedirectMode == InputRedirectMode.NONE)
                        return super.commitText(text, newCursorPosition);
                    int count = text == null ? 0 : Character.codePointCount(text, 0, text.length());
                    Log.d(TAG, "IME commit in redirected mode, codePoints=" + count);
                    int offset = 0;
                    for (int i = 0; i < count; i++) {
                        int codePoint = Character.codePointAt(text, offset);
                        int codePointLength = Character.charCount(codePoint);
                        if (inputRedirectMode == InputRedirectMode.NOTE_NOVEL
                                && !NovelInputPolicy.shouldRedirect(codePoint)) {
                            super.commitText(new String(Character.toChars(codePoint)), 1);
                        } else if (inputRedirectMode == InputRedirectMode.LEGACY_FAKE_PAGE
                                && codePoint == ' ') {
                            insertText(" ");
                        } else if (!emitNextBookChar()
                                && inputRedirectMode == InputRedirectMode.NOTE_NOVEL) {
                            inputRedirectMode = InputRedirectMode.NONE;
                            stopNoteNovelInputAtBookEnd(false);
                            return super.commitText(text.subSequence(offset, text.length()),
                                    newCursorPosition);
                        }
                        offset += codePointLength;
                    }
                    return true;
                }

                @Override public boolean setComposingText(CharSequence text, int newCursorPosition) {
                    if (inputRedirectMode == InputRedirectMode.LEGACY_FAKE_PAGE) return true;
                    if (inputRedirectMode == InputRedirectMode.NOTE_NOVEL && text != null) {
                        for (int offset = 0; offset < text.length();) {
                            int codePoint = Character.codePointAt(text, offset);
                            if (NovelInputPolicy.shouldRedirect(codePoint)) return true;
                            offset += Character.charCount(codePoint);
                        }
                    }
                    return super.setComposingText(text, newCursorPosition);
                }
            };
        }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            return handleBossKey(event) || super.onKeyDown(keyCode, event);
        }

        @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
            return handleBossKey(event) || super.onKeyUp(keyCode, event);
        }
    }
}
