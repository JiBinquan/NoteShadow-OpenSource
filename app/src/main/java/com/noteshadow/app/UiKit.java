package com.noteshadow.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/** Small dependency-free helpers for the Graphite Blue programmatic screens. */
public final class UiKit {
    private UiKit() { }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable roundedBackground(Context context, int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable borderedBackground(Context context, int fillColor,
                                                       int borderColor, int radiusDp) {
        GradientDrawable drawable = roundedBackground(context, fillColor, radiusDp);
        drawable.setStroke(dp(context, 1), borderColor);
        return drawable;
    }

    public static void applySurface(View view, Context context) {
        view.setBackground(borderedBackground(context,
                context.getResources().getColor(R.color.graphite_surface),
                context.getResources().getColor(R.color.graphite_border), 12));
        view.setElevation(dp(context, 1));
    }

    public static void applySelectedSurface(View view, Context context) {
        view.setBackground(roundedBackground(context,
                context.getResources().getColor(R.color.graphite_selected), 8));
    }

    public static Button primaryButton(Context context, CharSequence text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        int surface = context.getResources().getColor(R.color.graphite_surface);
        int muted = context.getResources().getColor(R.color.graphite_selected);
        button.setTextColor(stateColors(muted, surface, surface));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{-android.R.attr.state_enabled}, roundedBackground(context,
                context.getResources().getColor(R.color.graphite_primary_disabled), 8));
        background.addState(new int[]{android.R.attr.state_pressed}, roundedBackground(context,
                context.getResources().getColor(R.color.graphite_primary_pressed), 8));
        background.addState(new int[]{}, roundedBackground(context,
                context.getResources().getColor(R.color.graphite_primary), 8));
        button.setBackground(background);
        button.setMinHeight(dp(context, 48));
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        return button;
    }

    public static Button secondaryButton(Context context, CharSequence text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        int primary = context.getResources().getColor(R.color.graphite_primary);
        int secondary = context.getResources().getColor(R.color.graphite_text_secondary);
        button.setTextColor(stateColors(secondary, primary, primary));
        StateListDrawable background = new StateListDrawable();
        int surface = context.getResources().getColor(R.color.graphite_surface);
        int border = context.getResources().getColor(R.color.graphite_border);
        background.addState(new int[]{-android.R.attr.state_enabled}, borderedBackground(context,
                context.getResources().getColor(R.color.graphite_background), border, 8));
        background.addState(new int[]{android.R.attr.state_pressed}, borderedBackground(context,
                context.getResources().getColor(R.color.graphite_selected), primary, 8));
        background.addState(new int[]{}, borderedBackground(context, surface, border, 8));
        button.setBackground(background);
        button.setMinHeight(dp(context, 48));
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        return button;
    }

    private static ColorStateList stateColors(int disabled, int pressed, int normal) {
        return new ColorStateList(new int[][]{
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_pressed},
                new int[]{}
        }, new int[]{disabled, pressed, normal});
    }

    public static void applyBodyText(TextView view, Context context) {
        view.setTextColor(context.getResources().getColor(R.color.graphite_text_primary));
        view.setTextSize(16);
    }

    public static void applySecondaryText(TextView view, Context context) {
        view.setTextColor(context.getResources().getColor(R.color.graphite_text_secondary));
        view.setTextSize(14);
    }

    /** Applies the shared screen-title typography without changing layout ownership. */
    public static void applyTitle(TextView view, Context context) {
        view.setTextColor(context.getResources().getColor(R.color.graphite_text_primary));
        view.setTextSize(22);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    /** Applies the smaller section-heading typography used by management screens. */
    public static void applySectionTitle(TextView view, Context context) {
        view.setTextColor(context.getResources().getColor(R.color.graphite_text_primary));
        view.setTextSize(17);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
    }

    /** Applies the subdued explanatory/hint style. */
    public static void applyHint(TextView view, Context context) {
        applySecondaryText(view, context);
        view.setAlpha(0.92f);
    }

    /** Makes an EditText fit the graphite surfaces while keeping native input behavior. */
    public static void applyInput(EditText view, Context context) {
        view.setTextColor(context.getResources().getColor(R.color.graphite_text_primary));
        view.setHintTextColor(context.getResources().getColor(R.color.graphite_text_secondary));
        view.setTextSize(16);
        view.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        view.setBackground(borderedBackground(context,
                context.getResources().getColor(R.color.graphite_surface),
                context.getResources().getColor(R.color.graphite_border), 8));
    }

    /** Creates a destructive action button with pressed and disabled states. */
    public static Button dangerButton(Context context, CharSequence text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        int surface = context.getResources().getColor(R.color.graphite_surface);
        button.setTextColor(stateColors(context.getResources().getColor(R.color.graphite_danger_disabled),
                surface, surface));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{-android.R.attr.state_enabled}, roundedBackground(context,
                context.getResources().getColor(R.color.graphite_danger_disabled), 8));
        background.addState(new int[]{android.R.attr.state_pressed}, roundedBackground(context,
                context.getResources().getColor(R.color.graphite_danger_pressed), 8));
        background.addState(new int[]{}, roundedBackground(context,
                context.getResources().getColor(R.color.graphite_danger), 8));
        button.setBackground(background);
        button.setMinHeight(dp(context, 48));
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        return button;
    }

    /** Applies the standard raised card surface used on management pages. */
    public static void applyCard(View view, Context context) {
        applySurface(view, context);
        view.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
    }

    /** Applies a neutral empty-state surface; callers supply the state text. */
    public static void applyEmptyState(View view, Context context) {
        view.setBackground(borderedBackground(context,
                context.getResources().getColor(R.color.graphite_selected),
                context.getResources().getColor(R.color.graphite_border), 12));
        view.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20));
    }

    /** Creates a one-pixel divider view in the shared border color. */
    public static View divider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(context.getResources().getColor(R.color.graphite_border));
        divider.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1, dp(context, 1)));
        return divider;
    }

    /** Applies a compact pill/badge appearance to a text view. */
    public static void applyBadge(TextView view, Context context) {
        view.setTextColor(context.getResources().getColor(R.color.graphite_primary));
        view.setTextSize(12);
        view.setGravity(android.view.Gravity.CENTER);
        view.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
        view.setBackground(roundedBackground(context,
                context.getResources().getColor(R.color.graphite_badge), 999));
    }
}
