package com.noteshadow.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Project/session management page. It deliberately contains no reading content. */
public final class HistoryActivity extends Activity {
    private static final int REQUEST_EXPORT = 4201;
    private static final String STATE_PENDING_EXPORT_KIND = "pending_export_kind";
    private static final String STATE_PENDING_SESSION_IDS = "pending_session_ids";
    private static final String STATE_ARCHIVED = "history_archived";
    private static final String STATE_TRASH_MODE = "history_trash_mode";
    private static final String STATE_SEARCH = "history_search";
    private static final String STATE_SELECTION_MODE = "history_selection_mode";
    private static final String STATE_SELECTED_IDS = "history_selected_ids";
    private static final String STATE_LIST_POSITION = "history_list_position";
    private final ArrayList<SessionItem> items = new ArrayList<>();
    private final ArrayList<TrashedSessionRecord> trashItems = new ArrayList<>();
    private final ArrayList<String> labels = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ArrayAdapter<String> adapter;
    private ListView list;
    private EditText search;
    private TextView projectHeading;
    private Button archiveToggle;
    private Button selectButton;
    private Button exportButton;
    private Button bundleExportButton;
    private Button fullBundleExportButton;
    private Button trashButton;
    private Button projectButton;
    private ProjectRepository projects;
    private ProjectRecord currentProject;
    private boolean archived;
    private boolean selectionMode;
    private boolean exportInProgress;
    private boolean trashMode;
    private int loadGeneration;
    /** Only used by the API 23-28 document picker flow; API 29+ uses a local list. */
    private List<SessionRecord> pendingPickerExport;
    private ArrayList<String> pendingPickerSessionIds;
    private ExportKind pendingPickerKind;
    private boolean restoreSelectionMode;
    private boolean restoringSearch;
    private ArrayList<String> restoreSelectedIds;
    private int restoreListPosition = -1;

    private enum ExportKind { TRANSCRIPTS, NOTE_PACKAGE, FULL_PACKAGE }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            String pending = state.getString(STATE_PENDING_EXPORT_KIND);
            if (pending != null) try { pendingPickerKind = ExportKind.valueOf(pending); }
            catch (IllegalArgumentException ignored) { pendingPickerKind = null; }
            pendingPickerSessionIds = state.getStringArrayList(STATE_PENDING_SESSION_IDS);
            exportInProgress = pendingPickerKind != null && pendingPickerSessionIds != null;
            archived = state.getBoolean(STATE_ARCHIVED, false);
            trashMode = state.getBoolean(STATE_TRASH_MODE, false);
            restoreSelectionMode = state.getBoolean(STATE_SELECTION_MODE, false) && !trashMode;
            restoreSelectedIds = state.getStringArrayList(STATE_SELECTED_IDS);
            restoreListPosition = state.getInt(STATE_LIST_POSITION, -1);
        }
        setTitle("记录");
        projects = new ProjectRepository(getFilesDir());
        buildUi();
        if (state != null) {
            restoringSearch = true;
            search.setText(state.getString(STATE_SEARCH, ""));
            restoringSearch = false;
            updateArchiveButton();
            applyTrashVisibility();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        boolean landscape = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        root.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));
        root.setBackgroundColor(getResources().getColor(R.color.graphite_background));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);

        LinearLayout projectRow = new LinearLayout(this);
        projectRow.setOrientation(landscape ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        projectRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        projectHeading = new TextView(this);
        UiKit.applyTitle(projectHeading, this);
        projectRow.addView(projectHeading, landscape
                ? new LinearLayout.LayoutParams(-1, -2)
                : new LinearLayout.LayoutParams(0, -2, 1));
        projectButton = UiKit.secondaryButton(this, "项目");
        projectButton.setOnClickListener(v -> showProjectDialog());
        projectRow.addView(projectButton, landscape ? fullWidthButtonParams(true) : buttonParams(false));
        trashButton = UiKit.secondaryButton(this, "回收站");
        trashButton.setOnClickListener(v -> setTrashMode(!trashMode));
        projectRow.addView(trashButton, landscape ? fullWidthButtonParams(true) : buttonParams(true));
        controls.addView(projectRow);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索转写内容");
        UiKit.applyInput(search, this);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!restoringSearch) scheduleLoad();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        controls.addView(search, verticalParams(dp(10)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(landscape ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        archiveToggle = UiKit.secondaryButton(this, "");
        archiveToggle.setOnClickListener(v -> {
            archived = !archived;
            updateArchiveButton();
            loadItems();
        });
        actions.addView(archiveToggle, landscape ? fullWidthButtonParams(false) : weightedButtonParams(false));
        selectButton = UiKit.secondaryButton(this, "选择导出");
        selectButton.setOnClickListener(v -> toggleSelectionMode());
        actions.addView(selectButton, landscape ? fullWidthButtonParams(true) : weightedButtonParams(true));
        controls.addView(actions, verticalParams(dp(10)));

        LinearLayout exportActions = new LinearLayout(this);
        // Keep each export action at least 48dp tall and full width on narrow screens.
        // This avoids three long Chinese labels competing for one horizontal row.
        exportActions.setOrientation(LinearLayout.VERTICAL);
        exportButton = UiKit.secondaryButton(this, "导出转写包");
        exportButton.setEnabled(false);
        exportButton.setOnClickListener(v -> exportSelected());
        exportActions.addView(exportButton, fullWidthButtonParams(false));
        bundleExportButton = UiKit.secondaryButton(this, "导出笔记包");
        bundleExportButton.setEnabled(false);
        bundleExportButton.setOnClickListener(v -> exportSelectedPackages(false));
        exportActions.addView(bundleExportButton, fullWidthButtonParams(true));
        fullBundleExportButton = UiKit.secondaryButton(this, "导出完整课程包");
        fullBundleExportButton.setEnabled(false);
        fullBundleExportButton.setOnClickListener(v -> exportSelectedPackages(true));
        exportActions.addView(fullBundleExportButton, fullWidthButtonParams(true));
        controls.addView(exportActions, verticalParams(dp(8)));

        if (landscape) {
            ScrollView controlScroll = new ScrollView(this);
            controlScroll.setFillViewport(true);
            controlScroll.addView(controls, new ScrollView.LayoutParams(-1, -2));
            LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(0, -1, 0.34f);
            controlParams.rightMargin = dp(12);
            root.addView(controlScroll, controlParams);
        } else {
            root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        }

        list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_NONE);
        list.setDivider(new android.graphics.drawable.ColorDrawable(
                getResources().getColor(R.color.graphite_border)));
        list.setDividerHeight(dp(1));
        list.setBackgroundColor(getResources().getColor(R.color.graphite_background));
        labels.add("正在加载记录…");
        setListAdapter(false);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (trashMode) {
                if (position == 0 || position - 1 >= trashItems.size()) return;
                if (!selectionMode) startActivity(new Intent(this, SessionDetailActivity.class)
                        .putExtra(SessionDetailActivity.EXTRA_SESSION_ID, trashItems.get(position - 1).getSessionId())
                        .putExtra(SessionDetailActivity.EXTRA_TRASH_MODE, true));
                return;
            }
            if (position >= items.size()) return;
            if (selectionMode) {
                updateSelectionButtons();
                return;
            }
            startActivity(new Intent(this, SessionDetailActivity.class)
                    .putExtra(SessionDetailActivity.EXTRA_SESSION_ID, items.get(position).id));
        });
        if (landscape) root.addView(list, new LinearLayout.LayoutParams(0, -1, 0.66f));
        else root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        updateArchiveButton();
        refreshProjectHeading();
    }

    private LinearLayout.LayoutParams buttonParams(boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(48));
        if (withStartMargin) params.leftMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams weightedButtonParams(boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        if (withStartMargin) params.leftMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams fullWidthButtonParams(boolean withTopMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        if (withTopMargin) params.topMargin = dp(6);
        return params;
    }

    private LinearLayout.LayoutParams verticalParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = topMargin;
        return params;
    }

    private void refreshProjectHeading() {
        AppStorage storage = new AppStorage(this);
        currentProject = projects.load(storage.currentProjectId());
        if (currentProject == null) currentProject = projects.load(ProjectRecord.DEFAULT_ID);
        projectHeading.setText(trashMode ? "回收站" : (currentProject == null ? "当前项目" : currentProject.getName()));
    }

    private void setTrashMode(boolean enabled) {
        if (selectionMode) exitSelectionMode();
        trashMode = enabled;
        applyTrashVisibility();
        loadItems();
    }

    private void applyTrashVisibility() {
        projectHeading.setText(trashMode ? "回收站" : (currentProject == null ? "当前项目" : currentProject.getName()));
        search.setVisibility(trashMode ? android.view.View.GONE : android.view.View.VISIBLE);
        projectButton.setVisibility(trashMode ? android.view.View.GONE : android.view.View.VISIBLE);
        archiveToggle.setVisibility(trashMode ? android.view.View.GONE : android.view.View.VISIBLE);
        selectButton.setVisibility(trashMode ? android.view.View.GONE : android.view.View.VISIBLE);
        exportButton.setVisibility(trashMode ? android.view.View.GONE : android.view.View.VISIBLE);
        bundleExportButton.setVisibility(trashMode ? android.view.View.GONE : android.view.View.VISIBLE);
        fullBundleExportButton.setVisibility(trashMode ? android.view.View.GONE : android.view.View.VISIBLE);
        trashButton.setText(trashMode ? "返回记录" : "回收站");
    }

    private void updateArchiveButton() {
        if (archiveToggle != null) archiveToggle.setText(archived ? "查看正常记录" : "查看已归档");
    }

    private void toggleSelectionMode() {
        if (selectionMode) exitSelectionMode();
        else {
            selectionMode = true;
            list.clearChoices();
            list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
            setListAdapter(true);
            selectButton.setText("取消选择");
            updateSelectionButtons();
        }
    }

    private void exitSelectionMode() {
        selectionMode = false;
        list.clearChoices();
        list.setChoiceMode(ListView.CHOICE_MODE_NONE);
        setListAdapter(false);
        list.requestLayout();
        selectButton.setText("选择导出");
        exportButton.setEnabled(false);
        bundleExportButton.setEnabled(false);
        fullBundleExportButton.setEnabled(false);
    }

    private void setListAdapter(boolean selectable) {
        int layout = selectable ? android.R.layout.simple_list_item_multiple_choice
                : android.R.layout.simple_list_item_1;
        adapter = new ArrayAdapter<>(this, layout, labels);
        list.setAdapter(adapter);
    }

    private void updateSelectionButtons() {
        boolean hasSelection = false;
        for (int i = 0; i < items.size(); i++) hasSelection |= list.isItemChecked(i);
        exportButton.setEnabled(selectionMode && hasSelection && !exportInProgress);
        if (bundleExportButton != null) bundleExportButton.setEnabled(selectionMode && hasSelection && !exportInProgress);
        if (fullBundleExportButton != null) fullBundleExportButton.setEnabled(selectionMode && hasSelection && !exportInProgress);
    }

    private void scheduleLoad() {
        handler.removeCallbacksAndMessages("history-load");
        handler.postAtTime(this::loadItems, "history-load", System.currentTimeMillis() + 260);
    }

    private void loadItems() {
        // A new project/query/archive dataset invalidates every old row position.
        if (selectionMode) exitSelectionMode();
        final int generation = ++loadGeneration;
        if (trashMode) {
            new Thread(() -> {
                TrashRepository repository = new TrashRepository(getFilesDir());
                ArrayList<TrashedSessionRecord> loaded = new ArrayList<>(repository.list());
                SessionStorageUsage usage = repository.usage();
                ArrayList<String> loadedLabels = new ArrayList<>();
                loadedLabels.add("回收站占用：文本 " + readableSize(usage.getTextBytes()) + "，录音 "
                        + readableSize(usage.getRecordingBytes()) + "，图片 " + readableSize(usage.getAttachmentBytes())
                        + "，合计 " + readableSize(usage.getTotalBytes()));
                for (TrashedSessionRecord item : loaded) loadedLabels.add(trashLabel(item));
                if (loaded.isEmpty()) loadedLabels.add("暂无可恢复记录");
                runOnUiThread(() -> {
                    if (!isAlive() || generation != loadGeneration) return;
                    trashItems.clear(); trashItems.addAll(loaded); items.clear(); labels.clear();
                    labels.addAll(loadedLabels);
                    adapter.notifyDataSetChanged();
                    if (restoreListPosition >= 0) {
                        list.setSelection(Math.min(restoreListPosition, Math.max(0, labels.size() - 1)));
                        restoreListPosition = -1;
                    }
                });
            }, "trash-loader").start();
            return;
        }
        final String projectId = currentProject == null ? ProjectRecord.DEFAULT_ID : currentProject.getProjectId();
        final String query = search == null ? "" : search.getText().toString();
        new Thread(() -> {
            ArrayList<SessionItem> loaded = new ArrayList<>();
            for (SessionRecord record : new SessionRepository(getFilesDir()).list(projectId, archived, query)) {
                loaded.add(new SessionItem(record));
            }
            runOnUiThread(() -> {
                if (!isAlive() || generation != loadGeneration) return;
                items.clear();
                items.addAll(loaded);
                labels.clear();
                for (SessionItem item : items) labels.add(item.label());
                if (labels.isEmpty()) labels.add(archived ? "暂无已归档记录" : "暂无记录");
                adapter.notifyDataSetChanged();
                restoreSelectionAfterLoad();
                if (restoreListPosition >= 0) {
                    list.setSelection(Math.min(restoreListPosition, Math.max(0, labels.size() - 1)));
                    restoreListPosition = -1;
                }
            });
        }, "history-loader").start();
    }

    private void restoreSelectionAfterLoad() {
        if (!restoreSelectionMode || trashMode) return;
        selectionMode = true;
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        setListAdapter(true);
        for (int i = 0; i < items.size(); i++) {
            if (restoreSelectedIds != null && restoreSelectedIds.contains(items.get(i).id)) {
                list.setItemChecked(i, true);
            }
        }
        selectButton.setText("取消选择");
        updateSelectionButtons();
        restoreSelectionMode = false;
        restoreSelectedIds = null;
    }

    private void showProjectDialog() {
        if (RecordingService.isActive() || RealtimeTranscriptionService.isActive()) {
            showToast("请先停止当前录音或实时转写");
            return;
        }
        List<ProjectRecord> all = projects.list();
        String[] names = new String[all.size() + 2];
        for (int i = 0; i < all.size(); i++) names[i] = all.get(i).getName();
        names[all.size()] = "新建项目";
        names[all.size() + 1] = "重命名当前项目";
        new AlertDialog.Builder(this).setTitle("选择项目").setItems(names, (dialog, which) -> {
            if (which < all.size()) {
                new AppStorage(this).setCurrentProjectId(all.get(which).getProjectId());
                refreshProjectHeading();
                loadItems();
            } else if (which == all.size()) {
                askProjectName(false);
            } else {
                askProjectName(true);
            }
        }).show();
    }

    private void askProjectName(boolean rename) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("项目名称");
        new AlertDialog.Builder(this).setTitle(rename ? "重命名项目" : "新建项目").setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    // Copy UI text before leaving the main thread; EditText is not thread-safe.
                    final String name = input.getText().toString();
                    final String projectId = currentProject == null ? ProjectRecord.DEFAULT_ID : currentProject.getProjectId();
                    new Thread(() -> {
                        try {
                            ProjectRecord result = rename ? projects.rename(projectId, name) : projects.create(name);
                            new AppStorage(this).setCurrentProjectId(result.getProjectId());
                            runOnUiThread(() -> {
                                if (!isAlive()) return;
                                refreshProjectHeading();
                                loadItems();
                            });
                        } catch (Exception e) {
                            showToast("项目名称不可用");
                        }
                    }, "project-update").start();
                }).show();
    }

    private void exportSelected() {
        if (exportInProgress) return;
        ArrayList<SessionRecord> selected = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) if (list.isItemChecked(i)) selected.add(items.get(i).record);
        ArrayList<SessionRecord> exportable = new ArrayList<>();
        for (SessionRecord record : selected) if (!record.getMeetingNote().trim().isEmpty()) exportable.add(record);
        if (exportable.isEmpty()) {
            showToast(selected.isEmpty() ? "请先选择记录" : "所选记录没有正式转写");
            return;
        }
        exportInProgress = true;
        exportButton.setEnabled(false);
        updateSelectionButtons();
        String name = "记录-" + System.currentTimeMillis() + ".zip";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            setPendingPickerExport(exportable, ExportKind.TRANSCRIPTS);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, name);
            try {
                startActivityForResult(intent, REQUEST_EXPORT);
            } catch (Exception e) {
                clearPendingPickerExport();
                exportInProgress = false;
                updateSelectionButtons();
                showToast("无法打开导出位置");
            }
            return;
        }
        final List<SessionRecord> records = exportable;
        new Thread(() -> exportToMediaStore(records, name, ExportKind.TRANSCRIPTS), "bundle-export").start();
    }

    private void exportSelectedPackages(boolean full) {
        if (exportInProgress) return;
        ArrayList<SessionRecord> selected = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) if (list.isItemChecked(i)) selected.add(items.get(i).record);
        ArrayList<SessionRecord> exportable = new ArrayList<>();
        if (full) {
            exportable.addAll(selected);
        } else {
            SessionRepository repository = new SessionRepository(getFilesDir());
            for (SessionRecord record : selected) {
                SessionStorageUsage usage = repository.usage(record.getSessionId());
                if (!record.getTimestampNote().trim().isEmpty() || usage.getAttachmentBytes() > 0) exportable.add(record);
            }
        }
        if (exportable.isEmpty()) {
            showToast(selected.isEmpty() ? "请先选择记录" : "所选记录没有笔记或图片");
            return;
        }
        exportInProgress = true; updateSelectionButtons();
        ExportKind kind = full ? ExportKind.FULL_PACKAGE : ExportKind.NOTE_PACKAGE;
        String name = (full ? "完整课程包-" : "笔记包-") + System.currentTimeMillis() + ".zip";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            setPendingPickerExport(exportable, kind);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip"); intent.putExtra(Intent.EXTRA_TITLE, name);
            try { startActivityForResult(intent, REQUEST_EXPORT); } catch (Exception e) {
                clearPendingPickerExport(); exportInProgress = false; updateSelectionButtons(); showToast("无法打开导出位置");
            }
            return;
        }
        new Thread(() -> exportToMediaStore(exportable, name, kind), "course-package-export").start();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT) return;
        List<SessionRecord> records = pendingPickerExport == null
                ? reloadPendingRecords() : pendingPickerExport;
        ExportKind kind = pendingPickerKind;
        clearPendingPickerExport();
        if (resultCode != RESULT_OK || data == null || data.getData() == null
                || records == null || records.isEmpty() || kind == null) {
            exportInProgress = false;
            updateSelectionButtons();
            if (resultCode == RESULT_OK && kind != null) showToast("所选记录已变化，未能导出");
            return;
        }
        final Uri destination = data.getData();
        new Thread(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(destination, "w")) {
                if (out == null) throw new Exception("no output");
                int count = writeExport(kind, records, out);
                showToast("已导出 " + count + " 条记录");
            } catch (Exception e) {
                showToast("导出失败");
            } finally {
                exportInProgress = false;
                runOnUiThread(this::updateSelectionButtons);
            }
        }, "bundle-export").start();
    }

    private void setPendingPickerExport(List<SessionRecord> records, ExportKind kind) {
        pendingPickerExport = records;
        pendingPickerKind = kind;
        pendingPickerSessionIds = new ArrayList<>();
        for (SessionRecord record : records) pendingPickerSessionIds.add(record.getSessionId());
    }

    private List<SessionRecord> reloadPendingRecords() {
        ArrayList<SessionRecord> records = new ArrayList<>();
        if (pendingPickerSessionIds == null) return records;
        SessionRepository repository = new SessionRepository(getFilesDir());
        for (String id : pendingPickerSessionIds) {
            SessionRecord record = repository.load(id);
            if (record != null) records.add(record);
        }
        return records;
    }

    private void clearPendingPickerExport() {
        pendingPickerExport = null;
        pendingPickerSessionIds = null;
        pendingPickerKind = null;
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean(STATE_ARCHIVED, archived);
        state.putBoolean(STATE_TRASH_MODE, trashMode);
        state.putString(STATE_SEARCH, search == null ? "" : search.getText().toString());
        state.putBoolean(STATE_SELECTION_MODE, selectionMode);
        state.putInt(STATE_LIST_POSITION, list == null ? 0 : list.getFirstVisiblePosition());
        ArrayList<String> selectedIds = new ArrayList<>();
        if (selectionMode && list != null && !trashMode) {
            for (int i = 0; i < items.size(); i++) if (list.isItemChecked(i)) selectedIds.add(items.get(i).id);
        }
        state.putStringArrayList(STATE_SELECTED_IDS, selectedIds);
        if (pendingPickerKind != null && pendingPickerSessionIds != null) {
            state.putString(STATE_PENDING_EXPORT_KIND, pendingPickerKind.name());
            state.putStringArrayList(STATE_PENDING_SESSION_IDS,
                    new ArrayList<>(pendingPickerSessionIds));
        }
    }

    private void exportToMediaStore(List<SessionRecord> records, String name, ExportKind kind) {
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NoteShadow");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("insert failed");
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new Exception("no output");
                writeExport(kind, records, out);
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Downloads.IS_PENDING, 0);
            if (getContentResolver().update(uri, ready, null, null) <= 0) throw new Exception("publish failed");
            showToast("已导出到 Downloads/NoteShadow");
        } catch (Exception e) {
            if (uri != null) try { getContentResolver().delete(uri, null, null); } catch (Throwable ignored) { }
            showToast("导出失败");
        } finally {
            exportInProgress = false;
            runOnUiThread(this::updateSelectionButtons);
        }
    }

    private int writeExport(ExportKind kind, List<SessionRecord> records, OutputStream out) throws Exception {
        if (kind == ExportKind.FULL_PACKAGE)
            return FullCourseBundleExporter.writeZip(getFilesDir(), records, out);
        if (kind == ExportKind.NOTE_PACKAGE)
            return CourseNoteBundleExporter.writeZip(getFilesDir(), records, out);
        return TranscriptBundleExporter.writeZip(records, out);
    }

    private boolean isAlive() {
        return !isFinishing() && (Build.VERSION.SDK_INT < 17 || !isDestroyed());
    }

    private String trashLabel(TrashedSessionRecord item) {
        ProjectRecord project = projects.load(item.getOriginalProjectId());
        String projectName = project == null ? item.getOriginalProjectId() : project.getName();
        SessionStorageUsage usage = new TrashRepository(getFilesDir()).usage(item.getSessionId());
        return item.getRecord().displayTitle() + " · " + projectName + "\n删除："
                + DateFormat.getDateTimeInstance().format(new Date(item.getDeletedAt()))
                + "\n文本 " + readableSize(usage.getTextBytes()) + " · 录音 " + readableSize(usage.getRecordingBytes())
                + " · 图片 " + readableSize(usage.getAttachmentBytes())
                + " · 合计 " + readableSize(usage.getTotalBytes());
    }

    private String readableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void showToast(String message) {
        if (!isAlive()) return;
        runOnUiThread(() -> { if (isAlive()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); });
    }

    @Override protected void onResume() {
        super.onResume();
        refreshProjectHeading();
        loadItems();
    }

    static int dpFor(android.content.Context c, int value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + .5f);
    }
    private int dp(int value) { return dpFor(this, value); }

    static final class SessionItem {
        final String id;
        final SessionRecord record;
        SessionItem(SessionRecord record) { this.record = record; this.id = record.getSessionId(); }
        String label() {
            String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(record.getUpdatedAt()));
            return record.displayTitle() + "  ·  " + time
                    + (record.getRecordings().size() > 0 ? "\n录音 " + record.getRecordings().size() + " 段" : "")
                    + "\n" + (record.preferredNote().trim().isEmpty() ? "暂无转写" : record.preview());
        }
    }
}
