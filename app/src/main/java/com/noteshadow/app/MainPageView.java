package com.noteshadow.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** View-only shell for the main screen. MainActivity owns all behavior/state. */
public final class MainPageView {
    private final Context context;
    private final LinearLayout root;
    private final FrameLayout contentHost;
    private final LinearLayout contextTools;
    private final TextView title;
    private final TextView status;
    private final Button recordsButton, newLessonButton, moreButton, settingsButton;
    private final Button noteButton, transcriptButton;
    private final Button singleOutputButton, dualOutputButton;
    private final Button recordButton, asrButton, fileAsrButton;
    private final Button editButton, previewButton, photoButton, imageButton;
    private final Drawable[] navBackgrounds = new Drawable[2];
    private final ColorStateList[] navTextColors = new ColorStateList[2];

    public MainPageView(Context context) {
        this.context = context;
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(8), dp(14), dp(8));
        root.setBackgroundColor(color(R.color.graphite_background));

        LinearLayout header = row();
        title = new TextView(context);
        title.setText("实时转写");
        title.setPadding(0, dp(8), dp(8), dp(8));
        UiKit.applyTitle(title, context);
        header.addView(title, new LinearLayout.LayoutParams(wrap(), wrap()));
        LinearLayout headerActions = row();
        recordsButton = flat("记录"); newLessonButton = flat("新课");
        moreButton = flat("更多"); settingsButton = flat("设置");
        add(headerActions, recordsButton); add(headerActions, newLessonButton);
        add(headerActions, moreButton); add(headerActions, settingsButton);
        singleOutputButton = flat("单线输出"); dualOutputButton = flat("双线输出");
        add(headerActions, singleOutputButton); add(headerActions, dualOutputButton);
        contextTools = row();
        editButton = flat("编辑"); previewButton = flat("预览");
        photoButton = flat("拍照"); imageButton = flat("图片");
        add(contextTools, editButton); add(contextTools, previewButton);
        add(contextTools, photoButton); add(contextTools, imageButton);
        headerActions.addView(contextTools, new LinearLayout.LayoutParams(wrap(), wrap()));
        header.addView(scroller(headerActions), new LinearLayout.LayoutParams(0, wrap(), 1));
        root.addView(header, new LinearLayout.LayoutParams(match(), wrap()));

        LinearLayout center = row();
        LinearLayout navigation = new LinearLayout(context);
        navigation.setOrientation(LinearLayout.VERTICAL);
        transcriptButton = nav("实时转写"); noteButton = nav("笔记");
        rememberNavStyles();
        navigation.addView(transcriptButton, navParams()); navigation.addView(noteButton, navParams());
        center.addView(navigation, new LinearLayout.LayoutParams(dp(96), match()));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12), dp(10), dp(12), dp(10));
        UiKit.applySurface(card, context);
        contentHost = new FrameLayout(context);
        card.addView(contentHost, new LinearLayout.LayoutParams(match(), 0, 1));
        center.addView(card, new LinearLayout.LayoutParams(0, match(), 1));
        root.addView(center, new LinearLayout.LayoutParams(match(), 0, 1));

        LinearLayout footer = row();
        LinearLayout footerActions = row();
        recordButton = danger("录音"); asrButton = primary("转写"); fileAsrButton = secondary("文件转");
        footerActions.addView(recordButton, footerButtonParams());
        footerActions.addView(asrButton, footerButtonParams());
        footerActions.addView(fileAsrButton, footerButtonParams());
        footer.addView(scroller(footerActions), new LinearLayout.LayoutParams(0, match(), 2));
        status = new TextView(context); UiKit.applySecondaryText(status, context); status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(dp(6), 0, 0, 0);
        status.setMaxLines(2); status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        footer.addView(status, new LinearLayout.LayoutParams(0, match(), 1));
        root.addView(footer, new LinearLayout.LayoutParams(match(), dp(56)));
    }

    private Button nav(String text) { Button b = flat(text); b.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT); b.setPadding(dp(10), 0, dp(6), 0); return b; }
    private LinearLayout.LayoutParams navParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(match(), dp(52)); p.setMargins(0, 0, 0, dp(8)); return p; }
    private LinearLayout row() { LinearLayout r = new LinearLayout(context); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); return r; }
    private HorizontalScrollView scroller(View child) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(child, new HorizontalScrollView.LayoutParams(wrap(), wrap()));
        return scroll;
    }
    private Button primary(String text) { Button b = style(UiKit.primaryButton(context, text)); b.setMinHeight(dp(44)); return b; }
    private Button danger(String text) { Button b = style(UiKit.dangerButton(context, text)); b.setMinHeight(dp(44)); return b; }
    private Button secondary(String text) { Button b = style(UiKit.secondaryButton(context, text)); b.setMinHeight(dp(44)); return b; }
    private Button flat(String text) {
        Button b = new Button(context); b.setText(text); b.setAllCaps(false); b.setTextSize(13);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(44)); b.setMinimumHeight(dp(44));
        b.setPadding(dp(9), 0, dp(9), 0); b.setTextColor(color(R.color.graphite_text_secondary));
        b.setBackground(new ColorDrawable(android.graphics.Color.TRANSPARENT)); return b;
    }
    private Button style(Button b) { b.setTextSize(13); b.setMinWidth(dp(68)); b.setMinHeight(dp(44)); return b; }
    private LinearLayout.LayoutParams footerButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(150), match());
        params.rightMargin = dp(6);
        return params;
    }
    private void add(LinearLayout parent, Button child) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(wrap(), wrap()); p.setMargins(0, 0, dp(6), 0); parent.addView(child, p); }
    private int dp(int value) { return UiKit.dp(context, value); }
    private int color(int id) { return context.getResources().getColor(id); }
    private int match() { return ViewGroup.LayoutParams.MATCH_PARENT; }
    private int wrap() { return ViewGroup.LayoutParams.WRAP_CONTENT; }

    private void rememberNavStyles() {
        Button[] buttons = {transcriptButton, noteButton};
        for (int i = 0; i < buttons.length; i++) {
            navBackgrounds[i] = buttons[i].getBackground();
            navTextColors[i] = buttons[i].getTextColors();
        }
    }

    public LinearLayout root() { return root; }
    public FrameLayout contentHost() { return contentHost; }
    public TextView title() { return title; }
    public TextView status() { return status; }
    public Button recordsButton() { return recordsButton; }
    public Button newLessonButton() { return newLessonButton; }
    public Button moreButton() { return moreButton; }
    public Button settingsButton() { return settingsButton; }
    public Button noteButton() { return noteButton; }
    public Button transcriptButton() { return transcriptButton; }
    public Button singleOutputButton() { return singleOutputButton; }
    public Button dualOutputButton() { return dualOutputButton; }
    public Button recordButton() { return recordButton; }
    public Button asrButton() { return asrButton; }
    public Button fileAsrButton() { return fileAsrButton; }
    public Button editButton() { return editButton; }
    public Button previewButton() { return previewButton; }
    public Button photoButton() { return photoButton; }
    public Button imageButton() { return imageButton; }
    public Button safePageButton() { return transcriptButton; }

    public void setActivePage(int activePage) {
        Button[] buttons = {transcriptButton, noteButton};
        for (int i = 0; i < buttons.length; i++) {
            if (i == activePage) {
                UiKit.applySelectedSurface(buttons[i], context);
                buttons[i].setTextColor(color(R.color.graphite_primary));
            } else {
                buttons[i].setBackground(navBackgrounds[i]);
                buttons[i].setTextColor(navTextColors[i]);
            }
        }
    }

    public void setOutputMode(int mode) {
        boolean visible = mode >= 0;
        singleOutputButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        dualOutputButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        singleOutputButton.setTextColor(color(mode == 0 ? R.color.graphite_primary : R.color.graphite_text_secondary));
        dualOutputButton.setTextColor(color(mode == 1 ? R.color.graphite_primary : R.color.graphite_text_secondary));
        if (mode == 0) UiKit.applySelectedSurface(singleOutputButton, context);
        else singleOutputButton.setBackground(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        if (mode == 1) UiKit.applySelectedSurface(dualOutputButton, context);
        else dualOutputButton.setBackground(new ColorDrawable(android.graphics.Color.TRANSPARENT));
    }

    /** Keeps page-specific tools out of the global toolbar while preserving MainActivity's visibility policy. */
    public void setPageActionsVisible(boolean visible) {
        contextTools.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
