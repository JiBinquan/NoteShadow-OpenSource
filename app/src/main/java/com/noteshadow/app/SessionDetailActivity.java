package com.noteshadow.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;

/** Read-only detail view for one saved record. */
public final class SessionDetailActivity extends Activity {
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_TRASH_MODE = "trash_mode";
    private static final int REQUEST_EXPORT = 4101;
    private static final String STATE_PENDING_PACKAGE = "pending_package";
    private static final String STATE_PENDING_TRANSCRIPT = "pending_transcript";
    private MediaPlayer player;
    private Button activeButton;
    private String activeIdleLabel = "";
    private SessionRecord record;
    private TrashedSessionRecord trashed;
    private boolean trashMode;
    private boolean trashOperationInProgress;
    private boolean exportInProgress;
    private Button transcriptExportButton;
    private Button notePackageExportButton;
    private Button fullPackageExportButton;
    private boolean pendingTranscriptExport;
    private PackageType pendingPackageExport = PackageType.NONE;

    private enum PackageType { NONE, NOTE, FULL }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            String pending = state.getString(STATE_PENDING_PACKAGE, PackageType.NONE.name());
            try { pendingPackageExport = PackageType.valueOf(pending); }
            catch (IllegalArgumentException ignored) { pendingPackageExport = PackageType.NONE; }
            pendingTranscriptExport = state.getBoolean(STATE_PENDING_TRANSCRIPT, false);
            exportInProgress = pendingPackageExport != PackageType.NONE || pendingTranscriptExport;
        }
        String id = getIntent().getStringExtra(EXTRA_SESSION_ID);
        trashMode = getIntent().getBooleanExtra(EXTRA_TRASH_MODE, false);
        setTitle(trashMode ? "回收站记录" : "记录详情");
        showLoading();
        new Thread(() -> {
            if (trashMode) trashed = id == null ? null : new TrashRepository(getFilesDir()).load(id);
            SessionRecord loaded = trashMode ? (trashed == null ? null : trashed.getRecord())
                    : (id == null ? null : new SessionRepository(getFilesDir()).load(id));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                record = loaded;
                buildUi();
            });
        }, "session-detail-loader").start();
    }

    private void showLoading() {
        TextView loading = new TextView(this);
        loading.setText("正在加载记录…");
        UiKit.applyBodyText(loading, this);
        loading.setGravity(android.view.Gravity.CENTER);
        loading.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 20), UiKit.dp(this, 20), UiKit.dp(this, 20));
        setContentView(loading);
    }

    private void buildUi() {
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 16), UiKit.dp(this, 16), UiKit.dp(this, 24));
        page.addView(content);
        TextView heading = new TextView(this);
        String projectName = "当前项目";
        if (record != null) {
            String projectId = trashMode && trashed != null ? trashed.getOriginalProjectId() : record.getProjectId();
            ProjectRecord project = new ProjectRepository(getFilesDir()).load(projectId);
            if (project != null) projectName = project.getName();
        }
        String displayTitle = record == null ? "记录详情"
                : (trashMode && trashed != null && !trashed.getOriginalTitle().trim().isEmpty()
                ? trashed.getOriginalTitle() : record.displayTitle());
        heading.setText(record == null ? "记录详情" : projectName + "\n" + displayTitle);
        UiKit.applyTitle(heading, this);
        content.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        addSpace(content, 12);
        if (record != null && !trashMode) {
            LinearLayout management = new LinearLayout(this);
            management.setOrientation(LinearLayout.VERTICAL);
            Button rename = UiKit.secondaryButton(this, "修改标题"); rename.setOnClickListener(v -> editTitle());
            management.addView(rename, fullWidthParams());
            addSpace(management, 8);
            Button archive = UiKit.secondaryButton(this, record.isArchived() ? "恢复记录" : "归档记录"); archive.setOnClickListener(v -> confirmArchive());
            management.addView(archive, fullWidthParams());
            content.addView(management);
            addSpace(content, 16);
        }
        TextView metadata = new TextView(this);
        SessionStorageUsage usage = record == null ? new SessionStorageUsage(0, 0)
                : (trashMode ? new TrashRepository(getFilesDir()).usage(record.getSessionId())
                : new SessionRepository(getFilesDir()).usage(record.getSessionId()));
        metadata.setText(record == null ? "找不到这条记录"
                : "创建时间：" + DateFormat.getDateTimeInstance().format(new Date(record.getCreatedAt()))
                + "\n更新时间：" + DateFormat.getDateTimeInstance().format(new Date(record.getUpdatedAt()))
                + "\n录音：" + record.getRecordings().size() + " 段"
                + "\n文本占用：" + readableSize(usage.getTextBytes())
                + " · 图片占用：" + readableSize(usage.getAttachmentBytes())
                + " · 录音占用：" + readableSize(usage.getRecordingBytes())
                + " · 合计：" + readableSize(usage.getTotalBytes()));
        UiKit.applySecondaryText(metadata, this);
        content.addView(card(metadata));
        addSpace(content, 16);

        LinearLayout recordings = new LinearLayout(this);
        recordings.setOrientation(LinearLayout.VERTICAL);
        addRecordingButtons(recordings);
        if (recordings.getChildCount() > 0) {
            content.addView(recordings);
            addSpace(content, 16);
        }

        if (trashMode) {
            Button restore = UiKit.primaryButton(this, "恢复到原项目");
            restore.setOnClickListener(v -> confirmRestore()); content.addView(restore, fullWidthParams());
            addSpace(content, 8);
            Button erase = UiKit.dangerButton(this, "永久删除");
            erase.setOnClickListener(v -> confirmPermanentDelete()); content.addView(erase, fullWidthParams());
        } else {
            transcriptExportButton = UiKit.secondaryButton(this, "导出正式转写");
            transcriptExportButton.setOnClickListener(v -> exportTranscript()); content.addView(transcriptExportButton, fullWidthParams());
            addSpace(content, 8);
            notePackageExportButton = UiKit.secondaryButton(this, "导出笔记包（笔记与图片）");
            notePackageExportButton.setOnClickListener(v -> exportPackage(PackageType.NOTE)); content.addView(notePackageExportButton, fullWidthParams());
            addSpace(content, 8);
            fullPackageExportButton = UiKit.primaryButton(this, "导出完整课程包");
            fullPackageExportButton.setOnClickListener(v -> exportPackage(PackageType.FULL)); content.addView(fullPackageExportButton, fullWidthParams());
            updateExportButtons();
            addSpace(content, 16);
            Button trash = UiKit.secondaryButton(this, "移入回收站");
            trash.setOnClickListener(v -> confirmTrash()); content.addView(trash, fullWidthParams());
        }
        addSpace(content, 20);
        TextView textHeading = new TextView(this); textHeading.setText("记录内容"); UiKit.applySectionTitle(textHeading, this);
        content.addView(textHeading);
        addSpace(content, 8);
        LinearLayout texts = new LinearLayout(this); texts.setOrientation(LinearLayout.VERTICAL);
        addText(texts, "正式转写", record == null ? "" : record.getMeetingNote());
        String draft = record == null ? "" : record.getDraftNote();
        if (!draft.trim().isEmpty()) addText(texts, "实时草稿", draft);
        String timestampNote = record == null ? "" : record.getTimestampNote();
        if (!timestampNote.trim().isEmpty()) addText(texts, "课堂笔记", timestampNote);
        content.addView(texts);
        setContentView(page);
    }

    private void addText(LinearLayout parent, String title, String value) {
        LinearLayout block = new LinearLayout(this); block.setOrientation(LinearLayout.VERTICAL);
        UiKit.applyCard(block, this);
        TextView label = new TextView(this); label.setText(title); UiKit.applySectionTitle(label, this);
        block.addView(label);
        TextView body = new TextView(this); body.setText(value.isEmpty() ? "（暂无）" : value);
        UiKit.applyBodyText(body, this); body.setTextIsSelectable(true);
        block.addView(body, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = UiKit.dp(this, 12);
        parent.addView(block, params);
    }

    private void addRecordingButtons(LinearLayout parent) {
        if (record == null || record.getRecordings().isEmpty()) return;
        TextView label = new TextView(this); label.setText("录音"); UiKit.applySectionTitle(label, this); parent.addView(label);
        addSpace(parent, 8);
        for (int i = 0; i < record.getRecordings().size(); i++) {
            File file = record.getRecordings().get(i);
            String idleLabel = "播放录音 " + (i + 1) + " · " + readableSize(file.length());
            Button button = UiKit.secondaryButton(this, idleLabel);
            button.setOnClickListener(v -> playOrStop(file, button, idleLabel));
            parent.addView(button, fullWidthParams());
            if (i + 1 < record.getRecordings().size()) addSpace(parent, 8);
        }
    }

    private LinearLayout.LayoutParams fullWidthParams() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void addSpace(LinearLayout parent, int dp) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, UiKit.dp(this, dp)));
    }

    private View card(View view) {
        UiKit.applyCard(view, this);
        return view;
    }

    private void playOrStop(File file, Button button, String idleLabel) {
        if (button == activeButton) { releasePlayer(); return; }
        releasePlayer();
        try {
            player = new MediaPlayer(); player.setDataSource(file.getAbsolutePath());
            activeButton = button;
            activeIdleLabel = idleLabel;
            button.setText("正在加载录音 " + (record.getRecordings().indexOf(file) + 1) + "…");
            player.setOnPreparedListener(mp -> {
                if (player != mp) return;
                mp.start();
                button.setText("停止播放");
            });
            player.setOnCompletionListener(mp -> {
                if (player == mp) releasePlayer();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                if (player == mp) {
                    releasePlayer();
                    Toast.makeText(this, "录音无法播放", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) { releasePlayer(); Toast.makeText(this, "录音无法播放", Toast.LENGTH_SHORT).show(); }
    }

    private void exportTranscript() {
        if (exportInProgress) return;
        String text = record == null ? "" : record.getMeetingNote();
        if (text.trim().isEmpty()) { Toast.makeText(this, "暂无正式转写", Toast.LENGTH_SHORT).show(); return; }
        setExportInProgress(true);
        String name = "记录-" + (record == null ? "未命名" : record.getSessionId()) + ".txt";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pendingTranscriptExport = true;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, name);
            try { startActivityForResult(intent, REQUEST_EXPORT); }
            catch (Exception e) {
                pendingTranscriptExport = false;
                setExportInProgress(false);
                Toast.makeText(this, "无法打开导出位置", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        exportToMediaStore(text, name);
    }

    private void exportPackage(PackageType type) {
        if (record == null || exportInProgress) return;
        setExportInProgress(true);
        String prefix = type == PackageType.FULL ? "完整课程包-" : "笔记包-";
        String name = prefix + CourseNoteBundleExporter.safe(record.getSessionId()) + ".zip";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            pendingPackageExport = type;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, name);
            try { startActivityForResult(intent, REQUEST_EXPORT); }
            catch (Exception e) {
                pendingPackageExport = PackageType.NONE;
                setExportInProgress(false);
                Toast.makeText(this, "无法打开导出位置", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        new Thread(() -> exportPackageToMediaStore(name, type), "course-package-export").start();
    }

    private void exportPackageToMediaStore(String name, PackageType type) {
        Uri uri = null;
        try {
            ContentValues values = new ContentValues(); values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NoteShadow");
            values.put(MediaStore.Downloads.IS_PENDING, 1); uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("insert failed");
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IOException("no output");
                writePackage(type, record, out);
            }
            ContentValues ready = new ContentValues(); ready.put(MediaStore.Downloads.IS_PENDING, 0);
            if (getContentResolver().update(uri, ready, null, null) <= 0)
                throw new IOException("publish failed");
            runOnUiThread(() -> Toast.makeText(this,
                    (type == PackageType.FULL ? "完整课程包" : "笔记包") + "已导出到 Downloads/NoteShadow",
                    Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            if (uri != null) try { getContentResolver().delete(uri, null, null); } catch (Throwable ignored) { }
            runOnUiThread(() -> Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show());
        } finally {
            runOnUiThread(() -> setExportInProgress(false));
        }
    }

    private void setExportInProgress(boolean value) {
        exportInProgress = value;
        updateExportButtons();
    }

    private void updateExportButtons() {
        boolean enabled = !exportInProgress;
        if (transcriptExportButton != null) transcriptExportButton.setEnabled(enabled);
        if (notePackageExportButton != null) notePackageExportButton.setEnabled(enabled);
        if (fullPackageExportButton != null) fullPackageExportButton.setEnabled(enabled);
    }

    private void writePackage(PackageType type, SessionRecord target, OutputStream out) throws IOException {
        if (type == PackageType.FULL)
            FullCourseBundleExporter.writeZip(getFilesDir(), java.util.Collections.singletonList(target), out);
        else CourseNoteBundleExporter.writeZip(getFilesDir(), java.util.Collections.singletonList(target), out);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString(STATE_PENDING_PACKAGE, pendingPackageExport.name());
        state.putBoolean(STATE_PENDING_TRANSCRIPT, pendingTranscriptExport);
    }

    private void editTitle() {
        if (record == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(record.getTitle());
        input.setHint("留空则使用默认标题");
        new AlertDialog.Builder(this).setTitle("修改记录标题").setView(input)
                .setNegativeButton("取消", null).setPositiveButton("保存", (dialog, which) -> {
                    String title = input.getText().toString().trim();
                    new Thread(() -> {
                        try {
                            new SessionRepository(getFilesDir()).updateTitle(record.getSessionId(), title);
                            runOnUiThread(() -> { Toast.makeText(this, "标题已保存", Toast.LENGTH_SHORT).show(); reload(); });
                        } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()); }
                    }, "session-title-update").start();
                }).show();
    }

    private void confirmArchive() {
        if (record == null) return;
        if (isCurrentSessionActive()) {
            Toast.makeText(this, "当前正在录音或实时转写，暂不能归档", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean restore = record.isArchived();
        new AlertDialog.Builder(this).setTitle(restore ? "恢复这条记录？" : "归档这条记录？")
                .setMessage(restore ? "记录会回到正常列表。" : "记录会从正常列表隐藏，但不会删除录音或文本。")
                .setNegativeButton("取消", null).setPositiveButton("确定", (dialog, which) -> new Thread(() -> {
                    try {
                        new SessionRepository(getFilesDir()).setArchived(record.getSessionId(), !restore);
                        runOnUiThread(() -> { Toast.makeText(this, restore ? "已恢复" : "已归档", Toast.LENGTH_SHORT).show(); finish(); });
                    } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show()); }
                }, "session-archive-update").start()).show();
    }

    private void confirmTrash() {
        if (record == null || trashOperationInProgress) return;
        if (isCurrentSessionActive()) {
            Toast.makeText(this, "当前正在录音或实时转写，暂不能移入回收站", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("移入回收站？")
                .setMessage("录音和文本会从记录列表移入回收站，可恢复。不会删除已导出的文件。")
                .setNegativeButton("取消", null).setPositiveButton("移入", (dialog, which) -> {
                    if (isCurrentSessionActive()) {
                        Toast.makeText(this, "当前已开始录音或实时转写，操作已取消", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    runTrashOperation(false);
                }).show();
    }

    private void runTrashOperation(boolean permanent) {
        if (trashOperationInProgress || record == null) return;
        trashOperationInProgress = true;
        new Thread(() -> {
            AppStorage storage = new AppStorage(this);
            String targetId = record.getSessionId();
            boolean currentSession = targetId.equals(storage.currentSessionId());
            boolean activeConflict = currentSession
                    && (RecordingService.isActive() || RealtimeTranscriptionService.isActive());
            boolean ok = !activeConflict && (permanent ? new TrashRepository(getFilesDir()).permanentlyDelete(targetId)
                    : new TrashRepository(getFilesDir()).trash(targetId));
            // MainActivity is paused while its history/detail pages are visible and has already
            // snapshotted the record. Rotate only after a successful move, so a failed recycle
            // operation never changes the user's current-session pointer.
            if (ok && !permanent && currentSession) storage.beginNewSession();
            final boolean conflict = activeConflict;
            runOnUiThread(() -> {
                trashOperationInProgress = false;
                if (!isAlive()) return;
                Toast.makeText(this, ok ? (permanent ? "已永久删除" : "已移入回收站")
                        : (conflict ? "当前仍在录音或实时转写，操作已取消"
                        : "操作失败，记录未改变"), Toast.LENGTH_SHORT).show();
                if (ok) finish();
            });
        }, permanent ? "session-permanent-delete" : "session-trash").start();
    }

    private void confirmRestore() {
        if (trashed == null || trashOperationInProgress) return;
        if (isCurrentSessionBusy()) {
            Toast.makeText(this, "当前正在录音或实时转写，暂不能恢复此记录", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("恢复这条记录？")
                .setMessage("将恢复到原项目、原标题和原归档状态。")
                .setNegativeButton("取消", null).setPositiveButton("恢复", (dialog, which) -> {
                    if (trashOperationInProgress || isCurrentSessionBusy()) {
                        Toast.makeText(this, "当前已开始录音或实时转写，操作已取消", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    trashOperationInProgress = true;
                    new Thread(() -> {
                        boolean ok = !isCurrentSessionBusy()
                                && new TrashRepository(getFilesDir()).restore(trashed.getSessionId());
                        runOnUiThread(() -> {
                            trashOperationInProgress = false;
                            if (!isAlive()) return;
                            Toast.makeText(this, ok ? "已恢复" : "恢复失败，记录仍在回收站", Toast.LENGTH_SHORT).show();
                            if (ok) finish();
                        });
                    }, "session-restore").start();
                }).show();
    }

    private void confirmPermanentDelete() {
        if (trashed == null || trashOperationInProgress) return;
        if (isCurrentSessionBusy()) {
            Toast.makeText(this, "当前正在录音或实时转写，暂不能永久删除此记录", Toast.LENGTH_SHORT).show();
            return;
        }
        SessionStorageUsage usage = new TrashRepository(getFilesDir()).usage(record.getSessionId());
        new AlertDialog.Builder(this).setTitle("永久删除？")
                .setMessage("第一步确认：将清理这条回收站记录，占用 " + readableSize(usage.getTotalBytes()) + "。")
                .setNegativeButton("取消", null).setPositiveButton("继续", (dialog, which) ->
                        new AlertDialog.Builder(this).setTitle("确定不可恢复地删除？")
                                .setMessage("第二步确认：应用内录音和文本将永久删除，无法恢复；Music/NoteShadow 中的公开录音副本及已导出文件需另行删除。空间："
                                        + readableSize(usage.getTotalBytes()) + "。")
                                .setNegativeButton("保留", null)
                                .setPositiveButton("永久删除", (d, w) -> {
                                    if (isCurrentSessionBusy()) {
                                        Toast.makeText(this, "当前已开始录音或实时转写，操作已取消", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    runTrashOperation(true);
                                }).show()).show();
    }

    private boolean isAlive() {
        return !isFinishing() && (Build.VERSION.SDK_INT < 17 || !isDestroyed());
    }

    private boolean isCurrentSessionActive() {
        String current = new AppStorage(this).currentSessionId();
        return record.getSessionId().equals(current)
                && (RecordingService.isActive() || RealtimeTranscriptionService.isActive());
    }

    private boolean isCurrentSessionBusy() {
        String current = new AppStorage(this).currentSessionId();
        return record != null && record.getSessionId().equals(current)
                && (RecordingService.isActive() || RealtimeTranscriptionService.isActive());
    }

    private void reload() {
        String id = getIntent().getStringExtra(EXTRA_SESSION_ID);
        new Thread(() -> {
            SessionRecord loaded = id == null ? null : new SessionRepository(getFilesDir()).load(id);
            runOnUiThread(() -> { if (!isFinishing() && !(android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed())) { record = loaded; buildUi(); } });
        }, "session-detail-reload").start();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT) return;
        if (pendingPackageExport != PackageType.NONE) {
            PackageType type = pendingPackageExport;
            pendingPackageExport = PackageType.NONE;
            SessionRecord target = currentOrReloadedRecord();
            if (resultCode != RESULT_OK || data == null || data.getData() == null || target == null) {
                setExportInProgress(false);
                if (resultCode == RESULT_OK) Toast.makeText(this, "记录已变化，未能导出", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri destination = data.getData();
            new Thread(() -> { try (OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (out == null) throw new IOException("no output");
                writePackage(type, target, out);
                runOnUiThread(() -> Toast.makeText(this,
                        type == PackageType.FULL ? "完整课程包已导出" : "笔记包已导出",
                        Toast.LENGTH_SHORT).show());
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()); }
            finally { runOnUiThread(() -> setExportInProgress(false)); }
            }, "course-package-export").start();
            return;
        }
        boolean transcript = pendingTranscriptExport;
        pendingTranscriptExport = false;
        SessionRecord target = transcript ? currentOrReloadedRecord() : null;
        String text = target == null ? null : target.getMeetingNote();
        if (resultCode != RESULT_OK || data == null || data.getData() == null || text == null) {
            setExportInProgress(false);
            if (resultCode == RESULT_OK && transcript)
                Toast.makeText(this, "记录已变化，未能导出", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = data.getData();
        new Thread(() -> {
            try {
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new Exception("no output");
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(() -> Toast.makeText(this, "已导出", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show());
            } finally {
                runOnUiThread(() -> setExportInProgress(false));
            }
        }, "session-export").start();
    }

    private SessionRecord currentOrReloadedRecord() {
        if (record != null) return record;
        String id = getIntent().getStringExtra(EXTRA_SESSION_ID);
        return id == null ? null : new SessionRepository(getFilesDir()).load(id);
    }

    private void exportToMediaStore(String text, String name) {
        new Thread(() -> {
            Uri uri = null;
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NoteShadow");
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("insert failed");
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new Exception("no output");
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Downloads.IS_PENDING, 0);
                if (getContentResolver().update(uri, ready, null, null) <= 0) throw new Exception("publish failed");
                runOnUiThread(() -> Toast.makeText(this, "已导出到 Downloads/NoteShadow", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                if (uri != null) try { getContentResolver().delete(uri, null, null); }
                catch (Throwable ignored) { }
                runOnUiThread(() -> Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show());
            } finally {
                runOnUiThread(() -> setExportInProgress(false));
            }
        }, "session-export").start();
    }

    private void releasePlayer() {
        if (activeButton != null) activeButton.setText(activeIdleLabel);
        activeButton = null;
        activeIdleLabel = "";
        if (player != null) { try { player.stop(); } catch (Exception ignored) {} player.release(); player = null; }
    }
    private String readableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }
    @Override protected void onStop() { releasePlayer(); super.onStop(); }
}
