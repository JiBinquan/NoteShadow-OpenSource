package com.noteshadow.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local bookshelf and reading-position screen. The storage implementation is kept behind a small adapter. */
public final class BookshelfActivity extends Activity {
    private static final int PICK_BOOK = 4301;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private Button add;
    private boolean importing;
    private BookshelfRepository repository;
    private int refreshSerial;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = newRepository();
        buildUi();
        refresh();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(16), pad, dp(16));
        root.setBackgroundColor(color(R.color.graphite_background));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = UiKit.secondaryButton(this, "返回");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(-2, -2));
        TextView title = text("资料管理", 24, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.leftMargin = dp(16);
        header.addView(title, titleParams);
        add = UiKit.primaryButton(this, "添加 TXT / EPUB");
        add.setOnClickListener(v -> chooseBook());
        header.addView(add, new LinearLayout.LayoutParams(-2, -2));
        root.addView(header);
        TextView hint = text("添加 TXT 或 EPUB，分别保存每本资料的阅读位置。选择资料后可用于假装输入。", 13, false);
        UiKit.applySecondaryText(hint, this);
        root.addView(hint, marginTop(10));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(4), 0, dp(24));
        scroll.addView(list); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void chooseBook() {
        if (importing) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        startActivityForResult(i, PICK_BOOK);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_BOOK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = displayName(uri).toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".txt") || name.endsWith(".epub"))) {
            toast("请选择 TXT 或 EPUB 文件"); return;
        }
        importBook(uri);
    }

    private void importBook(Uri uri) {
        importing = true; add.setEnabled(false); toast("正在导入资料…");
        io.execute(() -> {
            String error = null;
            try { repositoryImport(uri); } catch (Throwable e) { error = message(e); }
            String finalError = error;
            runOnUiThread(() -> {
                if (!alive()) return;
                importing = false; add.setEnabled(true);
                if (finalError != null) toast("导入失败：" + finalError);
                else { toast("资料导入成功"); refresh(); }
            });
        });
    }

    private void refresh() {
        final int request = ++refreshSerial;
        list.removeAllViews();
        addStateCard("正在读取资料…", "阅读位置和章节会分别保存在本机。");
        io.execute(() -> {
            try {
                List<BookRecord> books = repository.list();
                BookRecord current = repository.current();
                List<BookView> views = new ArrayList<>();
                for (BookRecord book : books) {
                    String content = repository.loadText(book);
                    views.add(new BookView(book, content.length(), chapterLabels(book, content),
                            current != null && current.getId().equals(book.getId())));
                }
                runOnUiThread(() -> {
                    if (!alive() || request != refreshSerial) return;
                    list.removeAllViews();
                    if (views.isEmpty()) {
                        addStateCard("尚未添加资料", "添加 TXT 或 EPUB 后，可分别保存每份资料的进度。");
                        return;
                    }
                    for (BookView view : views) addBookCard(view);
                });
            } catch (Throwable e) {
                String error = message(e);
                runOnUiThread(() -> {
                    if (!alive() || request != refreshSerial) return;
                    list.removeAllViews();
                    addStateCard("资料列表暂不可用", error);
                });
            }
        });
    }

    private void addBookCard(BookView view) {
        BookRecord book = view.book;
        boolean wide = getResources().getConfiguration().screenWidthDp >= 720;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        if (view.current) {
            card.setBackground(UiKit.borderedBackground(this,
                    color(R.color.graphite_surface), color(R.color.graphite_primary), 12));
            card.setElevation(dp(1));
        } else UiKit.applySurface(card, this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        String title = book.getTitle().isEmpty() ? "未命名资料" : book.getTitle();
        TextView name = text(title, 19, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        String type = book.getFormat();
        TextView typeBadge = badge(type.toUpperCase(Locale.ROOT), false);
        heading.addView(typeBadge);
        if (view.current) {
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
            badgeParams.leftMargin = dp(8);
            heading.addView(badge("当前使用", true), badgeParams);
        }
        content.addView(heading);

        String position = String.valueOf(book.getPosition());
        String length = String.valueOf(view.length);
        String percent = view.length == 0 ? "0.000" : String.format(Locale.getDefault(), "%.3f", 100.0 * book.getPosition() / Math.max(1, view.length));
        String chapter = view.chapterLabels.length > 1
                ? (book.getChapter() + 1) + "/" + view.chapterLabels.length : "";
        String recent = book.getLastReadAt() == 0 ? "未阅读" : android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", book.getLastReadAt()).toString();

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView progressLabel = text("阅读进度", 14, false);
        UiKit.applySecondaryText(progressLabel, this);
        progressRow.addView(progressLabel);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(view.length == 0 ? 0
                : (int)Math.min(1000, Math.max(0, book.getPosition() * 1000L / view.length)));
        bar.setProgressTintList(ColorStateList.valueOf(color(R.color.graphite_primary)));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(color(R.color.graphite_selected)));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(0, dp(8), 1);
        barParams.leftMargin = dp(14); barParams.rightMargin = dp(14);
        progressRow.addView(bar, barParams);
        TextView percentView = text(percent + "%", 14, true);
        progressRow.addView(percentView);
        content.addView(progressRow, marginTop(14));

        StringBuilder detail = new StringBuilder("字符位置  ").append(position).append(" / ").append(length);
        if (!chapter.isEmpty()) detail.append("    章节  ").append(chapter);
        detail.append("\n最近使用  ").append(recent);
        TextView info = text(detail.toString(), 14, false);
        UiKit.applySecondaryText(info, this);
        info.setLineSpacing(dp(4), 1f);
        content.addView(info, marginTop(10));

        LinearLayout.LayoutParams contentParams = wide
                ? new LinearLayout.LayoutParams(0, -2, 1)
                : new LinearLayout.LayoutParams(-1, -2);
        card.addView(content, contentParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        if (wide) actions.setPadding(dp(24), 0, 0, 0);
        else actions.setPadding(0, dp(16), 0, 0);
        Button use = UiKit.primaryButton(this, "设为当前");
        use.setOnClickListener(v -> runRepositoryAction(book));
        addAction(actions, use);
        Button progress = UiKit.secondaryButton(this, "设置进度");
        progress.setOnClickListener(v -> showProgressDialog(book));
        addAction(actions, progress);
        if (view.chapterLabels.length > 1) {
            Button chapters = UiKit.secondaryButton(this, "选择章节");
            chapters.setOnClickListener(v -> showChapterDialog(view));
            addAction(actions, chapters);
        }
        Button refresh = UiKit.secondaryButton(this, "刷新");
        refresh.setOnClickListener(v -> BookshelfActivity.this.refresh());
        addAction(actions, refresh);
        card.addView(actions, wide ? new LinearLayout.LayoutParams(dp(176), -2)
                : new LinearLayout.LayoutParams(-1, -2));
        list.addView(card, marginTop(12));
    }

    private void addStateCard(String heading, String detail) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(24), dp(32), dp(24), dp(32));
        UiKit.applySurface(card, this);
        card.addView(text(heading, 17, true));
        if (detail != null && !detail.trim().isEmpty()) {
            TextView message = text(detail, 14, false);
            UiKit.applySecondaryText(message, this);
            message.setGravity(Gravity.CENTER);
            card.addView(message, marginTop(8));
        }
        list.addView(card, marginTop(16));
    }

    private TextView badge(String label, boolean selected) {
        TextView badge = text(label, 12, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        if (selected) {
            UiKit.applySelectedSurface(badge, this);
            badge.setTextColor(color(R.color.graphite_primary));
        } else {
            badge.setBackground(UiKit.roundedBackground(this,
                    color(R.color.graphite_selected), 8));
            badge.setTextColor(color(R.color.graphite_text_secondary));
        }
        return badge;
    }

    private void addAction(LinearLayout actions, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        if (actions.getChildCount() > 0) params.topMargin = dp(8);
        actions.addView(button, params);
    }

    private void showProgressDialog(BookRecord book) {
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20), 0, dp(20), 0);
        EditText percent = new EditText(this); percent.setHint("百分比 0–100"); percent.setInputType(2 | 8192); body.addView(percent);
        EditText position = new EditText(this); position.setHint("或字符位置"); position.setInputType(2); body.addView(position);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("设置阅读进度").setView(body)
                .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    try {
                        final String pos = position.getText().toString().trim();
                        final String pct = percent.getText().toString().trim();
                        if (pos.isEmpty() && pct.isEmpty()) throw new IllegalArgumentException("请输入百分比或字符位置");
                        dialogButton(dialog, AlertDialog.BUTTON_POSITIVE, false);
                        io.execute(() -> {
                            try {
                                BookRecord updated;
                                if (!pos.isEmpty()) updated = repository.updateProgress(book.getId(), Integer.parseInt(pos), -1);
                                else updated = repository.setPercentage(book.getId(), pct);
                                syncLegacyMirror(updated);
                                runOnUiThread(() -> { if (alive()) { dialog.dismiss(); toast("阅读进度已保存"); refresh(); } });
                            } catch (Throwable e) {
                                String error = message(e);
                                runOnUiThread(() -> { if (alive()) { dialogButton(dialog, AlertDialog.BUTTON_POSITIVE, true); toast("设置失败：" + error); } });
                            }
                        });
                    } catch (Throwable e) { toast("设置失败：" + message(e)); }
                }));
        dialog.show();
    }

    private void showChapterDialog(BookView view) {
        int checked = Math.max(0, Math.min(view.book.getChapter(), view.chapterLabels.length - 1));
        new AlertDialog.Builder(this).setTitle("选择章节")
                .setSingleChoiceItems(view.chapterLabels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    io.execute(() -> {
                        try {
                            BookRecord updated = repository.selectChapter(view.book.getId(), which);
                            syncLegacyMirror(updated);
                            runOnUiThread(() -> {
                                if (!alive()) return;
                                toast("已跳到第 " + (which + 1) + " 章");
                                refresh();
                            });
                        } catch (Throwable e) {
                            String error = message(e);
                            runOnUiThread(() -> { if (alive()) toast("章节设置失败：" + error); });
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static String[] chapterLabels(BookRecord book, String content) {
        int[] starts = book.getChapterStarts();
        if (starts.length <= 1) return new String[0];
        String[] labels = new String[starts.length];
        for (int i = 0; i < starts.length; i++) {
            int start = BookProgress.clampToCodePointBoundary(content,
                    Math.max(0, Math.min(starts[i], content.length())));
            int end = i + 1 < starts.length
                    ? Math.max(start, Math.min(starts[i + 1], content.length())) : content.length();
            int sampleEnd = BookProgress.clampToCodePointBoundary(content,
                    Math.min(end, start + 240));
            String excerpt = content.substring(start, sampleEnd)
                    .replaceAll("\\s+", " ").trim();
            int points = excerpt.codePointCount(0, excerpt.length());
            if (points > 28) excerpt = excerpt.substring(0, excerpt.offsetByCodePoints(0, 28)) + "…";
            labels[i] = "第 " + (i + 1) + " 章" + (excerpt.isEmpty() ? "" : " · " + excerpt);
        }
        return labels;
    }

    private void runRepositoryAction(BookRecord book) {
        io.execute(() -> {
            try { BookRecord selected = repository.selectCurrent(book.getId()); syncLegacyMirror(selected); runOnUiThread(() -> { if (alive()) { toast("已设为当前资料"); refresh(); } }); }
            catch (Throwable e) { String error = message(e); runOnUiThread(() -> toast("操作失败：" + error)); }
        });
    }

    private BookshelfRepository newRepository() {
        return new BookshelfRepository(getFilesDir());
    }

    private void repositoryImport(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalStateException("无法读取文件");
            String name = displayName(uri);
            if (name == null) name = "book.txt";
            syncLegacyMirror(repository.importBook(in, name));
        }
    }
    private String displayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) { String n = c.getString(0); if (n != null && !n.trim().isEmpty()) return n; }
        } catch (Throwable ignored) { } finally { if (c != null) c.close(); }
        String p = uri == null ? null : uri.getLastPathSegment();
        return p == null || p.trim().isEmpty() ? "book.txt" : p;
    }
    private boolean alive() { return !isFinishing() && (android.os.Build.VERSION.SDK_INT < 17 || !isDestroyed()); }
    private void dialogButton(android.content.DialogInterface d, int which, boolean enabled) {
        if (d instanceof AlertDialog) { Button b = ((AlertDialog) d).getButton(which); if (b != null) b.setEnabled(enabled); }
    }
    /** Keeps services and downgrade builds on the currently selected book. Runs on the I/O executor. */
    private void syncLegacyMirror(BookRecord book) throws Exception {
        BookRecord current = repository.current();
        if (book == null || current == null || !book.getId().equals(current.getId())) return;
        String content = repository.loadText(current);
        new AppStorage(this).syncActiveBook(current.getTitle(), content, current.getPosition(),
                repository.chapterForPosition(current, current.getPosition()), current.getChapterStarts());
    }

    private static final class BookView {
        final BookRecord book;
        final int length;
        final String[] chapterLabels;
        final boolean current;
        BookView(BookRecord book, int length, String[] chapterLabels, boolean current) {
            this.book = book;
            this.length = length;
            this.chapterLabels = chapterLabels;
            this.current = current;
        }
    }
    private String message(Throwable e) { Throwable t = e; while (t.getCause() != null) t = t.getCause(); return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage(); }
    private TextView text(String s, float size, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color(R.color.graphite_text_primary)); if (bold) v.setTypeface(null, 1); return v; }
    private LinearLayout.LayoutParams marginTop(int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); return p; }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private int color(int id) { return getResources().getColor(id); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
