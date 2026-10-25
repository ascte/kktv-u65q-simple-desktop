package com.appletvstyle.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 「全部应用」全屏面板：暗色圆角列表，细体大字，焦点行高亮。
 * 只罗列可启动的应用；点击交回宿主启动，不卸载任何东西。
 */
final class AppsPanel extends FrameLayout {
    interface Actions {
        void open(String id);

        void dismissed();
    }

    private final Context context;
    private final LinearLayout list;
    private final List<View> rows = new ArrayList<>();
    Actions actions;

    AppsPanel(Context c, HomeItems items) {
        super(c);
        context = c;
        list = new LinearLayout(c);
        float density = c.getResources().getDisplayMetrics().density;

        View scrim = new View(c);
        scrim.setBackgroundColor(0xB0000000);
        addView(scrim, new LayoutParams(-1, -1));

        int pad = (int) (20 * density);
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0101418);
        bg.setCornerRadius(20 * density);
        bg.setStroke((int) (1 * density), 0x2EFFFFFF);
        panel.setBackground(bg);
        panel.setPadding(pad, pad, pad, (int) (12 * density));
        LayoutParams pp = new LayoutParams((int) (640 * density), -2, Gravity.CENTER);
        addView(panel, pp);

        TextView title = new TextView(c);
        title.setText("全部应用");
        title.setTextSize(22);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Fonts.light(c));
        title.setPadding((int) (12 * density), 0, 0, (int) (14 * density));
        panel.addView(title, new LinearLayout.LayoutParams(-2, -2));

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, (int) (560 * density));
        panel.addView(scroll, slp);

        build(items);
        setVisibility(GONE);
    }

    private void build(HomeItems items) {
        list.removeAllViews();
        rows.clear();
        List<String> ids = items.launchable();
        for (int i = 0; i < ids.size(); i++) {
            final String id = ids.get(i);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            int hpad = (int) (14 * getResources().getDisplayMetrics().density);
            row.setPadding(hpad, 0, hpad, 0);
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(HomeIcons.get(context, id, 0xFFFFFFFF));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                    (int) (28 * getResources().getDisplayMetrics().density),
                    (int) (28 * getResources().getDisplayMetrics().density));
            ip.rightMargin = (int) (16 * getResources().getDisplayMetrics().density);
            row.addView(icon, ip);
            TextView label = new TextView(context);
            label.setText(items.name(id));
            label.setTextSize(20);
            label.setTextColor(0xE6FFFFFF);
            label.setTypeface(Fonts.light(context));
            label.setSingleLine(true);
            row.addView(label, new LinearLayout.LayoutParams(-2, -2));
            row.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (actions != null) actions.open(id);
                    hide();
                }
            });
            row.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean focus) {
                    label.setTextColor(focus ? 0xFFFFFFFF : 0xC8FFFFFF);
                    GradientDrawable hl = new GradientDrawable();
                    hl.setColor(focus ? 0x33FFFFFF : Color.TRANSPARENT);
                    hl.setCornerRadius(10 * v.getResources().getDisplayMetrics().density);
                    v.setBackground(hl);
                }
            });
            list.addView(row, new LinearLayout.LayoutParams(-1, (int) (52 * getResources().getDisplayMetrics().density)));
            rows.add(row);
        }
    }

    void refresh(HomeItems items) {
        build(items);
    }

    boolean showing() {
        return getVisibility() == VISIBLE;
    }

    void show() {
        setVisibility(VISIBLE);
        if (!rows.isEmpty()) rows.get(0).requestFocus();
    }

    void hide() {
        setVisibility(GONE);
        if (actions != null) actions.dismissed();
    }
}
