package com.example.instasharedownloader;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(244, 246, 248);
    static final int SURFACE = Color.WHITE;
    static final int PANEL = Color.rgb(248, 250, 252);
    static final int TEXT = Color.rgb(22, 29, 36);
    static final int MUTED = Color.rgb(90, 100, 110);
    static final int FAINT = Color.rgb(134, 145, 156);
    static final int LINE = Color.rgb(225, 230, 235);
    static final int BRAND = Color.rgb(25, 111, 143);
    static final int BRAND_DARK = Color.rgb(14, 72, 96);
    static final int SUCCESS = Color.rgb(30, 132, 73);
    static final int WARNING = Color.rgb(184, 115, 28);
    static final int DANGER = Color.rgb(188, 54, 54);

    private Ui() {
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(Context context, int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable roundedStroke(Context context, int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = rounded(context, color, radiusDp);
        drawable.setStroke(dp(context, strokeDp), strokeColor);
        return drawable;
    }

    static LinearLayout card(Context context, int paddingDp, int radiusDp) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, paddingDp), dp(context, paddingDp), dp(context, paddingDp), dp(context, paddingDp));
        card.setBackground(roundedStroke(context, SURFACE, radiusDp, LINE, 1));
        return card;
    }

    static TextView text(Context context, String text, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    static TextView chip(Context context, String text, int textColor, int backgroundColor) {
        TextView chip = text(context, text, 12, textColor, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        chip.setBackground(rounded(context, backgroundColor, 999));
        return chip;
    }

    static void stylePrimary(Button button) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        button.setBackground(buttonBackground(context, BRAND, BRAND_DARK, Color.rgb(177, 190, 200), 14));
        button.setMinHeight(dp(context, 48));
    }

    static void styleSecondary(Button button) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setTextColor(BRAND_DARK);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        button.setBackground(buttonBackground(context, Color.rgb(230, 242, 247), Color.rgb(211, 232, 239), Color.rgb(236, 240, 243), 14));
        button.setMinHeight(dp(context, 48));
    }

    static void styleGhost(Button button) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setTextColor(MUTED);
        button.setBackground(buttonBackground(context, Color.TRANSPARENT, Color.rgb(239, 243, 246), Color.TRANSPARENT, 14));
        button.setMinHeight(dp(context, 44));
    }

    static void styleDanger(Button button) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setTextColor(DANGER);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        button.setBackground(buttonBackground(context, Color.rgb(255, 239, 239), Color.rgb(248, 223, 223), Color.rgb(236, 240, 243), 14));
        button.setMinHeight(dp(context, 48));
    }

    static void setMargins(View view, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.setMargins(dp(view.getContext(), left), dp(view.getContext(), top), dp(view.getContext(), right), dp(view.getContext(), bottom));
        view.setLayoutParams(params);
    }

    private static StateListDrawable buttonBackground(Context context, int normal, int pressed, int disabled, int radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, rounded(context, disabled, radiusDp));
        states.addState(new int[]{android.R.attr.state_pressed}, rounded(context, pressed, radiusDp));
        states.addState(new int[]{}, rounded(context, normal, radiusDp));
        return states;
    }
}
