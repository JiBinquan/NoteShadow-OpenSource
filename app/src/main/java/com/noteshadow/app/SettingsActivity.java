package com.noteshadow.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Plain-language app settings; kept separate from the recording screen. */
public final class SettingsActivity extends Activity {
    private static final int PICK_DS_KEY = 4201;
    private static final int BROWSE_PUBLIC_RECORDINGS = 4202;
    private static final int BROWSE_PUBLIC_EXPORTS = 4203;
    private TranscriptionSettingsRepository settings;
    private DeepSeekCredentialStore dsCredentials;
    private LmProviderConfigRepository lmProviders;
    private TextView dsKeyStatus;
    private RadioGroup modeGroup;
    private TextView modeHint;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new TranscriptionSettingsRepository(this);
        dsCredentials = new DeepSeekCredentialStore(this);
        lmProviders = new LmProviderConfigRepository(this);
        importStagedDsKey();
        int pad = dp(18);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, dp(12), pad, pad);
        content.setBackgroundColor(getResources().getColor(R.color.graphite_background));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("设置", 21, true); UiKit.applyTitle(title, this);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button back = UiKit.secondaryButton(this, "返回");
        back.setOnClickListener(v -> finish());
        header.addView(back); content.addView(header);
        LinearLayout modePanel = section(content, "实时识别方式");
        modeHint = text("识别正在进行时不能切换方式。", 13, false);
        UiKit.applyHint(modeHint, this); modePanel.addView(modeHint);
        modeGroup = new RadioGroup(this); modeGroup.setOrientation(RadioGroup.VERTICAL);
        addMode("仅 Qwen，省电", LiveTranscriptionMode.QWEN_ONLY);
        addMode("实时草稿 + Qwen", LiveTranscriptionMode.HYBRID);
        addMode("兼容模式（SenseVoice）", LiveTranscriptionMode.SENSEVOICE_FALLBACK);
        modePanel.addView(modeGroup);
        modeGroup.check(buttonId(settings.mode()));
        boolean active = RealtimeTranscriptionService.isActive();
        modeGroup.setEnabled(!active);
        for (int i = 0; i < modeGroup.getChildCount(); i++) modeGroup.getChildAt(i).setEnabled(!active);
        if (active) modeHint.setText("当前正在实时识别，请先停止后再切换方式。");
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (RealtimeTranscriptionService.isActive()) {
                modeGroup.check(buttonId(settings.mode()));
                Toast.makeText(this, "请先停止实时识别，再切换方式", Toast.LENGTH_SHORT).show(); return;
            }
            LiveTranscriptionMode chosen = modeForId(checkedId);
            if (chosen != null) settings.setMode(chosen);
        });
        LinearLayout keepPanel = section(content, "工作时屏幕");
        Switch keep = new Switch(this);
        keep.setText("识别或录音时保持屏幕常亮"); keep.setTextSize(16);
        keep.setTextColor(getResources().getColor(R.color.graphite_text_primary));
        keep.setChecked(getSharedPreferences(ActiveWorkPolicy.PREFERENCES, MODE_PRIVATE)
                .getBoolean(ActiveWorkPolicy.KEEP_SCREEN_ON, true));
        keep.setOnCheckedChangeListener((button, checked) -> getSharedPreferences(
                ActiveWorkPolicy.PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(ActiveWorkPolicy.KEEP_SCREEN_ON, checked).apply());
        keepPanel.addView(keep);
        TextView wakeHint = text("关闭后只允许屏幕自动熄灭，不会停止后台录音或识别。", 13, false);
        UiKit.applyHint(wakeHint, this); keepPanel.addView(wakeHint);
        LinearLayout dsPanel = section(content, "笔记问答");
        dsKeyStatus = text("", 14, false); dsPanel.addView(dsKeyStatus);
        updateDsKeyStatus();
        TextView privacy = text("/lm 发送当前问题及本课程先前成功的模型对话，不发送其他笔记、转写或录音。需要网络连接。", 13, false);
        UiKit.applyHint(privacy, this); dsPanel.addView(privacy);
        LinearLayout dsButtons = new LinearLayout(this);
        dsButtons.setOrientation(LinearLayout.VERTICAL);
        Button importKey = UiKit.secondaryButton(this, "导入密钥文件");
        importKey.setOnClickListener(v -> chooseDsKey()); dsButtons.addView(importKey, fullButtonParams());
        Button pasteKey = UiKit.secondaryButton(this, "粘贴密钥");
        pasteKey.setOnClickListener(v -> pasteDsKey()); dsButtons.addView(pasteKey, fullButtonParams());
        dsPanel.addView(dsButtons);
        Button clearKey = UiKit.dangerButton(this, "清除密钥");
        clearKey.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("清除所有模型密钥？")
                .setNegativeButton("取消", null).setPositiveButton("清除", (dialog, which) -> {
                    if (!lmProviders.clearAllCredentials())
                        Toast.makeText(this, "清除失败，请重试", Toast.LENGTH_SHORT).show();
                    updateDsKeyStatus();
                }).show());
        dsPanel.addView(clearKey, fullButtonParams());
        addLmProviderSettings(content);
        addStorageLocations(content);
        LinearLayout localPanel = section(content, "本机数据");
        TextView usage = text("正在计算记录占用……", 15, false);
        localPanel.addView(usage);
        loadStorageUsage(usage);
        LinearLayout infoPanel = section(content, "应用信息");
        infoPanel.addView(text("版本 " + versionName(), 15, false));
        ScrollView scroll = new ScrollView(this); scroll.addView(content); setContentView(scroll);
    }

    private void addLmProviderSettings(LinearLayout content) {
        LinearLayout panel = section(content, "模型接口（OpenAI 兼容）");
        LmProviderConfig current = lmProviders.current();
        EditText url = field(current.url(), "HTTPS 接口地址，例如 https://.../chat/completions");
        EditText model = field(current.modelId(), "模型 ID");
        EditText max = field(String.valueOf(current.maxOutputTokens()), "最大输出 token");
        EditText thinking = field(current.thinking(), "thinking 参数（可留空）");
        EditText timeout = field(String.valueOf(current.timeoutMs()), "超时毫秒数");
        EditText key = field("", "自定义接口 Key（留空保留现有 Key）");
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        panel.addView(url, fieldParams()); panel.addView(key, fieldParams()); panel.addView(model, fieldParams()); panel.addView(max, fieldParams());
        panel.addView(thinking, fieldParams()); panel.addView(timeout, fieldParams());
        Button save = UiKit.primaryButton(this, "保存模型接口");
        save.setOnClickListener(v -> {
            final String newUrl = url.getText().toString().trim();
            final String enteredKey = key.getText().toString();
            if (enteredKey.isEmpty() && !newUrl.equals(current.url())) {
                Toast.makeText(this, "切换接口地址时请填写该接口的 Key", Toast.LENGTH_LONG).show();
                return;
            }
            final String supplied = enteredKey.isEmpty() ? lmProviders.current().apiKey() : enteredKey;
            final String modelId = model.getText().toString();
            final String thinkingValue = thinking.getText().toString();
            final int maxValue, timeoutValue;
            try { maxValue = Integer.parseInt(max.getText().toString()); timeoutValue = Integer.parseInt(timeout.getText().toString()); }
            catch (Exception e) { Toast.makeText(this, "最大输出和超时必须是数字", Toast.LENGTH_LONG).show(); return; }
            save.setEnabled(false);
            new Thread(() -> {
                try {
                    String chosenModel = modelId;
                    if (!newUrl.equals(current.url()) && modelId.equals(LmProviderConfig.DEFAULT_MODEL)) {
                        java.util.List<String> choices;
                        try { choices = new LmChatClient().listModels(newUrl, supplied, timeoutValue); }
                        catch (Exception unavailable) {
                            throw new IllegalArgumentException("自动发现失败，请手动填写模型 ID");
                        }
                        if (choices.isEmpty()) throw new IllegalArgumentException("模型列表为空，请手动填写模型 ID");
                        chosenModel = choices.get(0);
                    }
                    lmProviders.save(newUrl, supplied, chosenModel, maxValue, thinkingValue, timeoutValue);
                    LmDiagnosticLog.record(getApplicationContext(), LmDiagnosticLog.Event.CONFIG_SAVED,
                            LmProviderConfig.DEFAULT_URL.equals(newUrl) ? "DEFAULT" : "CUSTOM");
                    final String savedModel = chosenModel;
                    runOnUiThread(() -> { save.setEnabled(true); key.setText(""); model.setText(savedModel); updateDsKeyStatus();
                        Toast.makeText(this, "模型接口设置已保存", Toast.LENGTH_SHORT).show(); });
                } catch (Exception e) {
                    LmDiagnosticLog.record(getApplicationContext(), LmDiagnosticLog.Event.CONFIG_REJECTED,
                            LmFailureCategory.of(e));
                    runOnUiThread(() -> { save.setEnabled(true);
                    Toast.makeText(this, "设置无效：" + e.getMessage(), Toast.LENGTH_LONG).show(); }); }
            }, "lm-provider-save").start();
        });
        panel.addView(save, fullButtonParams());
        Button discover = UiKit.secondaryButton(this, "发现模型（/models）");
        discover.setOnClickListener(v -> { discover.setEnabled(false); new Thread(() -> {
            try { java.util.List<String> models = new LmChatClient().listModels(this);
                runOnUiThread(() -> { discover.setEnabled(true); new AlertDialog.Builder(this).setTitle("可用模型")
                        .setMessage(models.isEmpty() ? "接口没有返回模型，请手动填写模型 ID。" : android.text.TextUtils.join("\n", models))
                        .setPositiveButton("确定", null).show(); });
            } catch (Exception e) { runOnUiThread(() -> { discover.setEnabled(true);
                Toast.makeText(this, "模型发现失败，可手动填写 Model ID", Toast.LENGTH_LONG).show(); }); }
        }, "lm-model-discovery").start(); });
        panel.addView(discover, fullButtonParams());
        Button connectionTest = UiKit.secondaryButton(this, "测试模型连接");
        connectionTest.setOnClickListener(v -> {
            connectionTest.setEnabled(false);
            LmDiagnosticLog.record(this, LmDiagnosticLog.Event.SETTINGS_TEST_STARTED, "FIXED_PROMPT");
            new Thread(() -> {
                long started = System.currentTimeMillis();
                try {
                    new LmChatClient().answer(getApplicationContext(),
                            java.util.Collections.singletonList(new LmConversation.Message(
                                    "user", "Reply only OK.")));
                    long seconds = Math.min(999, (System.currentTimeMillis() - started) / 1000);
                    LmDiagnosticLog.record(getApplicationContext(),
                            LmDiagnosticLog.Event.SETTINGS_TEST_SUCCESS, "S" + seconds);
                    runOnUiThread(() -> { connectionTest.setEnabled(true);
                        Toast.makeText(this, "模型接口连接成功", Toast.LENGTH_LONG).show(); });
                } catch (Exception failure) {
                    String category = LmFailureCategory.of(failure);
                    LmDiagnosticLog.record(getApplicationContext(),
                            LmDiagnosticLog.Event.SETTINGS_TEST_FAILURE, category);
                    runOnUiThread(() -> { connectionTest.setEnabled(true);
                        Toast.makeText(this, "连接失败：" + LmFailureCategory.userHint(category),
                                Toast.LENGTH_LONG).show(); });
                }
            }, "lm-settings-test").start();
        });
        panel.addView(connectionTest, fullButtonParams());
        TextView testHint = text("连接测试只发送固定测试句，不读取当前课程的笔记或对话。", 13, false); UiKit.applyHint(testHint, this); panel.addView(testHint);
        TextView quickHint = text("快速配置：填写新接口地址和 Key，保留默认模型 ID 时会先尝试 /models 自动选择；若接口不支持模型发现，请手动填写模型 ID。", 13, false); UiKit.applyHint(quickHint, this); panel.addView(quickHint);
        TextView safeHint = text("仅发送当前课程的 user/assistant 消息；不会读取其他笔记、转写或录音。", 13, false); UiKit.applyHint(safeHint, this); panel.addView(safeHint);
        addLmDiagnosticPanel(content);
    }

    private void addLmDiagnosticPanel(LinearLayout content) {
        LinearLayout panel = section(content, "模型诊断记录");
        TextView diagHint = text("只记录请求状态、耗时和错误类别；不记录问题、回答、密钥、接口地址或录音。", 13, false); UiKit.applyHint(diagHint, this); panel.addView(diagHint);
        TextView recent = text(LmDiagnosticLog.recent(this), 13, false);
        recent.setTextIsSelectable(true);
        recent.setPadding(dp(10), dp(10), dp(10), dp(10));
        UiKit.applySurface(recent, this); panel.addView(recent);
        LinearLayout buttons = new LinearLayout(this);
        Button refresh = UiKit.secondaryButton(this, "刷新记录");
        refresh.setOnClickListener(v -> recent.setText(LmDiagnosticLog.recent(this)));
        buttons.addView(refresh, new LinearLayout.LayoutParams(0, -2, 1));
        Button clear = UiKit.dangerButton(this, "清空记录");
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("清空模型诊断记录？")
                .setNegativeButton("取消", null).setPositiveButton("清空", (dialog, which) -> {
                    if (!LmDiagnosticLog.clear(this))
                        Toast.makeText(this, "清空失败", Toast.LENGTH_SHORT).show();
                    recent.setText(LmDiagnosticLog.recent(this));
                }).show());
        buttons.addView(clear, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(buttons);
    }

    private void addStorageLocations(LinearLayout content) {
        LinearLayout panel = section(content, "存储与目录");
        TextView hint = text("当前仍使用默认保存位置。本阶段只提供位置说明、占用信息和快捷查看，不会移动或改写任何文件。", 13, false);
        UiKit.applyHint(hint, this); panel.addView(hint);
        LinearLayout locations = new LinearLayout(this);
        locations.setOrientation(LinearLayout.VERTICAL);
        panel.addView(locations);
        TextView loading = text("正在读取存储位置……", 14, false);
        locations.addView(loading);
        new Thread(() -> {
            File files = getFilesDir();
            StorageCategoryUsage active = StorageCategoryUsage.measure(new File(files, "sessions"));
            StorageCategoryUsage trash = StorageCategoryUsage.measure(new File(files, "trash"));
            java.util.List<StorageLocationInfo> values = StorageLocationInfo.defaults(
                    active.plus(trash), Build.VERSION.SDK_INT >= 29);
            runOnUiThread(() -> {
                locations.removeAllViews();
                for (StorageLocationInfo value : values) locations.addView(storageLocationCard(value));
            });
        }, "settings-storage-locations").start();
    }

    private android.view.View storageLocationCard(StorageLocationInfo value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(9), dp(12), dp(9));
        UiKit.applySurface(card, this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(8);
        card.setLayoutParams(cardParams);
        card.addView(text(value.label(), 16, true));
        TextView location = text(value.pathDescription(), 13, false);
        UiKit.applySecondaryText(location, this);
        card.addView(location);
        TextView size = text("课程资料占用 " + readableSize(value.bytes()), 13, false);
        UiKit.applySecondaryText(size, this);
        card.addView(size);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        if (value.systemOpenable() && value.kind() == StorageLocationInfo.Kind.RECORDINGS) {
            buttons.addView(storageButton("浏览公开录音", () -> browsePublicFiles(
                    "Music/NoteShadow", "audio/*", BROWSE_PUBLIC_RECORDINGS,
                    StorageLocationInfo.DEFAULT_PUBLIC_RECORDINGS_PATH)));
        } else if (value.systemOpenable() && (value.kind() == StorageLocationInfo.Kind.TRANSCRIPTS
                || value.kind() == StorageLocationInfo.Kind.NOTES)) {
            buttons.addView(storageButton("浏览导出文件", () -> browsePublicFiles(
                    "Download/NoteShadow", "*/*", BROWSE_PUBLIC_EXPORTS,
                    StorageLocationInfo.DEFAULT_TEXT_EXPORT_PATH)));
        }
        buttons.addView(storageButton("查看课程记录", () ->
                startActivity(new Intent(this, HistoryActivity.class))));
        card.addView(buttons);
        return card;
    }

    private Button storageButton(String label, Runnable action) {
        Button button = UiKit.secondaryButton(this, label);
        button.setOnClickListener(v -> action.run());
        button.setLayoutParams(fullButtonParams());
        return button;
    }

    private void browsePublicFiles(String relativePath, String mimeType, int requestCode,
                                   String displayPath) {
        Uri directory = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:" + relativePath);
        Intent browse = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        browse.addCategory(Intent.CATEGORY_OPENABLE);
        browse.setType(mimeType);
        browse.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= 26)
            browse.putExtra("android.provider.extra.INITIAL_URI", directory);
        try {
            startActivityForResult(browse, requestCode);
        } catch (Throwable unavailable) {
            Toast.makeText(this, "请在系统文件管理器中浏览 " + displayPath,
                    Toast.LENGTH_LONG).show();
        }
    }

    private EditText field(String value, String hint) {
        EditText field = new EditText(this); field.setSingleLine(true); field.setText(value); field.setHint(hint);
        UiKit.applyInput(field, this); return field;
    }
    private void updateDsKeyStatus() {
        dsKeyStatus.setText(lmProviders.current().apiKey().isEmpty()
                ? "当前接口尚未配置密钥" : "当前接口密钥已保存在本机");
    }

    /** The desktop installer can stage a key once; plaintext is removed immediately. */
    private void importStagedDsKey() {
        try {
            dsCredentials.importStaged(this);
        } catch (Exception ignored) {
            Toast.makeText(this, "临时密钥导入失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseDsKey() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try { startActivityForResult(intent, PICK_DS_KEY); }
        catch (Exception e) { Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show(); }
    }

    private void pasteDsKey() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("粘贴 DeepSeek 密钥").setView(input)
                .setNegativeButton("取消", null).setPositiveButton("保存", (dialog, which) -> {
                    try {
                        dsCredentials.save(input.getText().toString());
                        input.setText("");
                        updateDsKeyStatus();
                    } catch (Exception e) { Toast.makeText(this, "密钥格式或保存失败", Toast.LENGTH_SHORT).show(); }
                }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BROWSE_PUBLIC_RECORDINGS || requestCode == BROWSE_PUBLIC_EXPORTS) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                openSelectedPublicFile(data.getData());
            return;
        }
        if (requestCode != PICK_DS_KEY || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("无法读取密钥文件");
                dsCredentials.save(readSmallKey(input));
                runOnUiThread(() -> { updateDsKeyStatus(); Toast.makeText(this, "密钥已导入", Toast.LENGTH_SHORT).show(); });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "密钥文件格式或导入失败", Toast.LENGTH_SHORT).show());
            }
        }, "ds-key-import").start();
    }

    private void openSelectedPublicFile(Uri uri) {
        String type = getContentResolver().getType(uri);
        Intent open = new Intent(Intent.ACTION_VIEW);
        open.setDataAndType(uri, type == null || type.isEmpty() ? "*/*" : type);
        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(open);
        } catch (Throwable unavailable) {
            Toast.makeText(this, "系统中没有可打开此文件的应用", Toast.LENGTH_LONG).show();
        }
    }

    private static String readSmallKey(InputStream input) throws Exception {
        byte[] buffer = new byte[513];
        int count = 0, next;
        while (count < buffer.length && (next = input.read(buffer, count, buffer.length - count)) > 0) count += next;
        if (count == buffer.length || input.read() >= 0) throw new IllegalStateException("密钥文件过长");
        return new String(buffer, 0, count, StandardCharsets.UTF_8);
    }
    private void addMode(String label, LiveTranscriptionMode mode) {
        RadioButton button = new RadioButton(this); button.setText(label); button.setTextSize(16);
        button.setId(buttonId(mode)); modeGroup.addView(button);
    }
    private int buttonId(LiveTranscriptionMode mode) { return 2000 + mode.ordinal(); }
    private LiveTranscriptionMode modeForId(int id) {
        int ordinal = id - 2000;
        return ordinal >= 0 && ordinal < LiveTranscriptionMode.values().length
                ? LiveTranscriptionMode.values()[ordinal] : null;
    }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(getResources().getColor(bold ? R.color.graphite_text_primary : R.color.graphite_text_secondary));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }
    private LinearLayout section(LinearLayout content, String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        UiKit.applyCard(panel, this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16);
        content.addView(panel, params);
        TextView heading = text(title, 17, true);
        UiKit.applySectionTitle(heading, this);
        panel.addView(heading);
        return panel;
    }
    private LinearLayout.LayoutParams fullButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        return p;
    }
    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        return p;
    }
    private LinearLayout.LayoutParams marginTop(int value) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(value); return p;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Throwable ignored) { return "未知"; }
    }

    private void loadStorageUsage(TextView target) {
        new Thread(() -> {
            File files = getFilesDir();
            SessionStorageUsage active = SessionStorageUsage.measure(new File(files, "sessions"));
            SessionStorageUsage trash = new TrashRepository(files).usage();
            SessionStorageUsage total = active.plus(trash.getTextBytes(), trash.getRecordingBytes(),
                    trash.getAttachmentBytes());
            String value = "文本 " + readableSize(total.getTextBytes()) + "  ·  录音 "
                    + readableSize(total.getRecordingBytes()) + "  ·  图片 " + readableSize(total.getAttachmentBytes()) + "\n合计 "
                    + readableSize(total.getTotalBytes()) + "（含回收站）";
            runOnUiThread(() -> target.setText(value));
        }, "settings-storage-usage").start();
    }

    private String readableSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        do { value /= 1024.0; unit++; }
        while (value >= 1024.0 && unit < units.length - 1);
        return String.format(Locale.getDefault(), value >= 100 ? "%.0f %s" : "%.1f %s",
                value, units[unit]);
    }
}
