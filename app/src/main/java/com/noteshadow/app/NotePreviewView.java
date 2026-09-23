package com.noteshadow.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Self-contained, cancellable Markdown note preview. */
public final class NotePreviewView extends FrameLayout {
    private static final int MAX_IMAGES = 48;
    private static final long MAX_IMAGE_PIXELS = 8_000_000L;
    private static final int MAX_TABLE_ROWS = 120;
    private static final int MAX_TABLE_COLUMNS = 16;
    private final NoteAttachmentRepository attachments;
    private final ScrollView scroll;
    private final TextView textView;
    private final ExecutorService imageWork = Executors.newSingleThreadExecutor();
    private final AtomicLong generations = new AtomicLong();
    private boolean visible;
    private boolean detached;
    private String currentSession = "";

    public NotePreviewView(Context context, NoteAttachmentRepository attachments) {
        super(context);
        this.attachments = attachments;
        textView = new TextView(context);
        textView.setTextSize(18);
        textView.setTextColor(context.getResources().getColor(R.color.graphite_text_primary));
        textView.setBackgroundColor(context.getResources().getColor(R.color.graphite_surface));
        textView.setPadding(dp(18), dp(18), dp(18), dp(18));
        textView.setLineSpacing(dp(3), 1.05f);
        textView.setTextIsSelectable(true);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(textView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setVisibility(View.GONE);
    }

    public void render(String sessionId, String markdown) {
        if (detached) return;
        currentSession = sessionId == null ? "" : sessionId;
        final long generation = generations.incrementAndGet();
        MarkdownNoteRenderer.Result rendered = MarkdownNoteRenderer.render(markdown == null ? "" : markdown);
        List<MarkdownNoteRenderer.TableBlock> tables = rendered.getTables();
        if (tables.isEmpty()) {
            textView.setText(styledMarkdown(rendered, 0, rendered.getText().length()));
            scroll.removeAllViews();
            scroll.addView(textView, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            Map<TextView, List<MarkdownNoteRenderer.Span>> owners = new HashMap<>();
            owners.put(textView, rendered.getSpans());
            Map<TextView, Integer> offsets = new HashMap<>();
            offsets.put(textView, 0);
            loadImages(currentSession, generation, owners, offsets);
            return;
        }
        LinearLayout document = new LinearLayout(getContext());
        document.setOrientation(LinearLayout.VERTICAL);
        document.setPadding(dp(18), dp(18), dp(18), dp(18));
        document.setBackgroundColor(getResources().getColor(R.color.graphite_surface));
        Map<TextView, List<MarkdownNoteRenderer.Span>> owners = new LinkedHashMap<>();
        Map<TextView, Integer> offsets = new LinkedHashMap<>();
        int cursor = 0;
        for (MarkdownNoteRenderer.TableBlock table : tables) {
            int start = Math.max(cursor, Math.min(table.getStart(), rendered.getText().length()));
            if (start > cursor) addTextBlock(document, rendered, cursor, start, owners, offsets);
            addTable(document, table, owners, offsets);
            cursor = Math.max(cursor, Math.min(table.getEnd(), rendered.getText().length()));
        }
        if (cursor < rendered.getText().length())
            addTextBlock(document, rendered, cursor, rendered.getText().length(), owners, offsets);
        scroll.removeAllViews();
        scroll.addView(document, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        loadImages(currentSession, generation, owners, offsets);
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) cancel();
        setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public boolean isVisible() { return visible; }

    public void cancel() { generations.incrementAndGet(); }

    @Override protected void onDetachedFromWindow() {
        detached = true;
        visible = false;
        cancel();
        imageWork.shutdownNow();
        super.onDetachedFromWindow();
    }

    private SpannableStringBuilder styledMarkdown(MarkdownNoteRenderer.Result rendered, int from, int to) {
        String text = rendered.getText().substring(from, to);
        SpannableStringBuilder styled = new SpannableStringBuilder(text);
        for (MarkdownNoteRenderer.Span span : rendered.getSpans()) {
            int start = Math.max(from, span.getStart());
            int end = Math.min(to, span.getEnd());
            if (end <= start) continue;
            start -= from; end -= from;
            switch (span.getStyle()) {
                case HEADING:
                    styled.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    styled.setSpan(new RelativeSizeSpan(1.22f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case BOLD: styled.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case ITALIC: styled.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case STRIKETHROUGH: styled.setSpan(new StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case CODE:
                    styled.setSpan(new TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    styled.setSpan(new BackgroundColorSpan(Color.rgb(238, 238, 238)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case LINK:
                    String safe = safeLink(span.getData());
                    if (safe != null) styled.setSpan(new URLSpan(safe), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    styled.setSpan(new ForegroundColorSpan(Color.rgb(30, 92, 170)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case IMAGE:
                    styled.setSpan(new ForegroundColorSpan(Color.rgb(105, 105, 105)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    styled.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case QUOTE: styled.setSpan(new QuoteSpan(Color.rgb(150, 150, 150)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case UNORDERED_LIST:
                case ORDERED_LIST: styled.setSpan(new LeadingMarginSpan.Standard(dp(8), dp(18)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case HORIZONTAL_RULE:
                    styled.setSpan(new ForegroundColorSpan(Color.rgb(155, 155, 155)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    styled.setSpan(new RelativeSizeSpan(0.85f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case TABLE: styled.setSpan(new TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); break;
                case TABLE_HEADER:
                    styled.setSpan(new TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    styled.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); break;
            }
        }
        return styled;
    }

    private void addTextBlock(LinearLayout document, MarkdownNoteRenderer.Result rendered, int from, int to,
                              Map<TextView, List<MarkdownNoteRenderer.Span>> owners, Map<TextView, Integer> offsets) {
        TextView block = new TextView(getContext());
        block.setTextSize(18); block.setTextColor(getResources().getColor(R.color.graphite_text_primary)); block.setLineSpacing(dp(3), 1.05f);
        block.setTextIsSelectable(true); block.setMovementMethod(LinkMovementMethod.getInstance());
        block.setText(styledMarkdown(rendered, from, to));
        document.addView(block, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        List<MarkdownNoteRenderer.Span> images = new ArrayList<>();
        for (MarkdownNoteRenderer.Span span : rendered.getSpans())
            if (span.getStyle() == MarkdownNoteRenderer.Style.IMAGE && span.getStart() >= from && span.getEnd() <= to) images.add(span);
        if (!images.isEmpty()) { owners.put(block, images); offsets.put(block, from); }
    }

    private void addTable(LinearLayout document, MarkdownNoteRenderer.TableBlock table,
                          Map<TextView, List<MarkdownNoteRenderer.Span>> owners, Map<TextView, Integer> offsets) {
        HorizontalScrollView horizontal = new HorizontalScrollView(getContext());
        horizontal.setFillViewport(false);
        TableLayout layout = new TableLayout(getContext()); layout.setStretchAllColumns(false); layout.setShrinkAllColumns(false);
        boolean truncated = table.getHeaderCells().size() > MAX_TABLE_COLUMNS || table.getRows().size() > MAX_TABLE_ROWS;
        addTableRow(layout, table.getHeaderTimestamp(), limitedCells(table.getHeaderCells()), table.getAlignments(), true, owners, offsets);
        int rowCount = Math.min(MAX_TABLE_ROWS, table.getRows().size());
        for (int i = 0; i < rowCount; i++) {
            MarkdownNoteRenderer.TableRow row = table.getRows().get(i);
            if (row.getCells().size() > MAX_TABLE_COLUMNS) truncated = true;
            addTableRow(layout, row.getTimestamp(), limitedCells(row.getCells()), table.getAlignments(), false, owners, offsets);
        }
        horizontal.addView(layout, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); params.bottomMargin = dp(12);
        document.addView(horizontal, params);
        if (!table.getSeparatorTimestamp().isEmpty()) {
            TextView stamp = new TextView(getContext()); stamp.setText("表格格式 " + table.getSeparatorTimestamp().trim()); stamp.setTextColor(Color.rgb(125,125,125)); stamp.setTextSize(11); stamp.setPadding(dp(4), dp(2), 0, dp(3));
            document.addView(stamp, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (truncated) {
            TextView warning = new TextView(getContext()); warning.setText("表格过大，预览仅显示前 " + MAX_TABLE_ROWS + " 行、" + MAX_TABLE_COLUMNS + " 列"); warning.setTextColor(Color.rgb(110,110,110)); warning.setTextSize(13);
            document.addView(warning, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private List<String> limitedCells(List<String> cells) { return new ArrayList<>(cells.subList(0, Math.min(MAX_TABLE_COLUMNS, cells.size()))); }
    private void addTableRow(TableLayout layout, String timestamp, List<String> cells, List<MarkdownNoteRenderer.Alignment> alignments, boolean header,
                             Map<TextView, List<MarkdownNoteRenderer.Span>> owners, Map<TextView, Integer> offsets) {
        TableRow row = new TableRow(getContext()); addTableCell(row, timestamp, MarkdownNoteRenderer.Alignment.LEFT, header, owners, offsets);
        for (int i = 0; i < cells.size(); i++) addTableCell(row, cells.get(i), i < alignments.size() ? alignments.get(i) : MarkdownNoteRenderer.Alignment.NONE, header, owners, offsets);
        layout.addView(row, new TableLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    private void addTableCell(TableRow row, String text, MarkdownNoteRenderer.Alignment alignment, boolean header,
                              Map<TextView, List<MarkdownNoteRenderer.Span>> owners, Map<TextView, Integer> offsets) {
        TextView cell = new TextView(getContext()); cell.setTextSize(16); cell.setTextColor(getResources().getColor(R.color.graphite_text_primary)); cell.setPadding(dp(9),dp(7),dp(9),dp(7)); cell.setMinWidth(dp(76)); cell.setMaxWidth(dp(280)); cell.setSingleLine(false); cell.setTextIsSelectable(true); cell.setMovementMethod(LinkMovementMethod.getInstance());
        cell.setGravity(alignment == MarkdownNoteRenderer.Alignment.CENTER ? Gravity.CENTER : alignment == MarkdownNoteRenderer.Alignment.RIGHT ? Gravity.RIGHT : Gravity.LEFT);
        MarkdownNoteRenderer.Result result = MarkdownNoteRenderer.render(text); cell.setText(styledMarkdown(result, 0, result.getText().length()));
        List<MarkdownNoteRenderer.Span> images = new ArrayList<>(); for (MarkdownNoteRenderer.Span span : result.getSpans()) if (span.getStyle() == MarkdownNoteRenderer.Style.IMAGE) images.add(span);
        if (!images.isEmpty()) { owners.put(cell, images); offsets.put(cell, 0); }
        if (header) cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable border = new GradientDrawable(); border.setColor(header ? getResources().getColor(R.color.graphite_selected) : getResources().getColor(R.color.graphite_surface)); border.setStroke(dp(1), getResources().getColor(R.color.graphite_border)); cell.setBackground(border);
        row.addView(cell, new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void loadImages(final String sessionId, final long generation,
                            Map<TextView, List<MarkdownNoteRenderer.Span>> owners, Map<TextView, Integer> offsets) {
        List<MarkdownNoteRenderer.Span> all = new ArrayList<>(); for (List<MarkdownNoteRenderer.Span> spans : owners.values()) all.addAll(spans);
        if (all.isEmpty()) return;
        int first = Math.max(0, all.size() - MAX_IMAGES); List<MarkdownNoteRenderer.Span> spans = new ArrayList<>(all.subList(first, all.size()));
        int maxWidth = Math.max(dp(120), getResources().getDisplayMetrics().widthPixels - dp(64)); int maxHeight = Math.max(dp(240), getResources().getDisplayMetrics().heightPixels);
        long perImagePixels = Math.max(1L, MAX_IMAGE_PIXELS / spans.size());
        imageWork.execute(() -> {
            Map<MarkdownNoteRenderer.Span, Bitmap> decoded = new HashMap<>();
            for (MarkdownNoteRenderer.Span span : spans) {
                if (Thread.currentThread().isInterrupted()) {
                    recycleAll(decoded);
                    return;
                }
                File image = attachments.resolve(sessionId, span.getData());
                Bitmap bitmap = decode(image, maxWidth, maxHeight, perImagePixels);
                if (bitmap != null) decoded.put(span, bitmap);
            }
            boolean posted = post(() -> {
                if (generation != generations.get() || !visible || !sessionId.equals(currentSession)) { for (Bitmap bitmap : decoded.values()) bitmap.recycle(); return; }
                for (Map.Entry<TextView, List<MarkdownNoteRenderer.Span>> owner : owners.entrySet()) {
                    SpannableStringBuilder withImages = new SpannableStringBuilder(owner.getKey().getText());
                    for (MarkdownNoteRenderer.Span span : owner.getValue()) { Bitmap bitmap = decoded.get(span); if (bitmap == null) continue; int offset = offsets.containsKey(owner.getKey()) ? offsets.get(owner.getKey()) : 0; int start = Math.max(0, Math.min(span.getStart() - offset, withImages.length())); int end = Math.max(start, Math.min(span.getEnd() - offset, withImages.length())); if (end <= start) continue; BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap); drawable.setBounds(0,0,drawable.getIntrinsicWidth(),drawable.getIntrinsicHeight()); withImages.setSpan(new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); }
                    owner.getKey().setText(withImages);
                }
            });
            if (!posted) recycleAll(decoded);
        });
    }
    private static void recycleAll(Map<MarkdownNoteRenderer.Span, Bitmap> decoded) {
        for (Bitmap bitmap : decoded.values()) bitmap.recycle();
    }
    private static Bitmap decode(File file, int maxWidth, int maxHeight, long maxPixels) {
        if (file == null || !file.isFile()) return null;
        try { BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true; BitmapFactory.decodeFile(file.getAbsolutePath(), bounds); if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null; int[] target = NoteImagePreviewSizing.fit(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight, maxPixels); if (target[0] <= 0 || target[1] <= 0) return null; int sample = 1; while (bounds.outWidth / sample > target[0] * 2 || bounds.outHeight / sample > target[1] * 2) sample *= 2; BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = Math.max(1, sample); options.inPreferredConfig = Bitmap.Config.RGB_565; Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options); if (decoded == null) return null; if (decoded.getWidth() == target[0] && decoded.getHeight() == target[1]) return decoded; Bitmap resized = Bitmap.createScaledBitmap(decoded, target[0], target[1], true); if (resized != decoded) decoded.recycle(); return resized; } catch (Throwable ignored) { return null; }
    }
    private static String safeLink(String target) { if (target == null) return null; String trimmed = target.trim(); for (int i=0;i<trimmed.length();i++) { char c=trimmed.charAt(i); if (Character.isWhitespace(c)||Character.isISOControl(c)) return null; } String lower=trimmed.toLowerCase(Locale.US); return lower.startsWith("https://")||lower.startsWith("http://") ? trimmed : null; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
