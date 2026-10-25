package com.appletvstyle.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 「设置」面板：壁纸轮播、间隔、文字大小、毛玻璃档位、背景。
 * 左/右键调整，立即生效；返回键关闭（由宿主统一处理）。
 */
final class SettingsPanel extends FrameLayout {
    interface Actions {
        void changed();          // 外观变化 → 重建首页

        void rotationChanged();  // 轮播开关/间隔变化

        void dismissed();
    }

    private static final String[] ROTATION = {"开", "关"};
    private static final String[] MINUTES = {"1 分钟", "5 分钟", "15 分钟", "30 分钟"};
    private static final String[] SIZE = {"标准", "大", "特大"};
    private static final String[] MATERIAL = {"轻", "中", "重"};
    private static final String[] BACKGROUND = {"照片", "纯黑"};

    private final Context context;
    private final HomeAppearance style;
    private final LinearLayout list;
    private final List<View> rows = new ArrayList<>();
    private final List<TextView> values = new ArrayList<>();
    private final List<Integer> currents = new ArrayList<>();
    Actions actions;

    SettingsPanel(Context c, HomeAppearance style) {
        super(c);
        context = c;
        this.style = style;
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
        panel.setPadding(pad, pad, pad, pad);
        LayoutParams pp = new LayoutParams((int) (520 * density), -2, Gravity.CENTER);
        addView(panel, pp);

        TextView title = new TextView(c);
        title.setText("设置");
        title.setTextSize(22);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Fonts.light(c));
        title.setPadding((int) (12 * density), 0, 0, (int) (14 * density));
        panel.addView(title, new LinearLayout.LayoutParams(-2, -2));

        panel.addView(list, new LinearLayout.LayoutParams(-1, -2));
        list.setOrientation(LinearLayout.VERTICAL);

        addRow("壁纸轮播", ROTATION, style.rotating() ? 0 : 1, new Apply() {
            @Override
            public void run(int idx) {
                SettingsPanel.this.style.setRotation(idx == 0, SettingsPanel.this.style.minutes());
                if (actions != null) actions.rotationChanged();
            }
        });
        addRow("轮播间隔", MINUTES, minutesIndex(style.minutes()), new Apply() {
            @Override
            public void run(int idx) {
                int[] mins = {1, 5, 15, 30};
                SettingsPanel.this.style.setRotation(SettingsPanel.this.style.rotating(), mins[idx]);
                if (actions != null) actions.rotationChanged();
            }
        });
        addRow("文字大小", SIZE, style.get("size"), new Apply() {
            @Override
            public void run(int idx) {
                SettingsPanel.this.style.set("size", idx);
                if (actions != null) actions.changed();
            }
        });
        addRow("焦点毛玻璃", MATERIAL, style.get("material"), new Apply() {
            @Override
            public void run(int idx) {
                SettingsPanel.this.style.set("material", idx);
                if (actions != null) actions.changed();
            }
        });
        addRow("背景", BACKGROUND, style.get("background"), new Apply() {
            @Override
            public void run(int idx) {
                SettingsPanel.this.style.set("background", idx);
                if (actions != null) actions.changed();
            }
        });

        setVisibility(GONE);
    }

    private interface Apply {
        void run(int idx);
    }

    private static int minutesIndex(int minutes) {
        int[] mins = {1, 5, 15, 30};
        for (int i = 0; i < mins.length; i++) if (mins[i] == minutes) return i;
        return 1;
    }

    private void addRow(String label, final String[] options, int current, final Apply apply) {
        float density = context.getResources().getDisplayMetrics().density;
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        int hpad = (int) (16 * density);
        row.setPadding(hpad, 0, hpad, 0);

        TextView name = new TextView(context);
        name.setText(label);
        name.setTextSize(19);
        name.setTextColor(0xE6FFFFFF);
        name.setTypeface(Fonts.light(context));
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView value = new TextView(context);
        value.setText(options[Math.max(0, Math.min(options.length - 1, current))]);
        value.setTextSize(19);
        value.setTextColor(0x9EFFFFFF);
        value.setTypeface(Fonts.light(context));
        row.addView(value, new LinearLayout.LayoutParams(-2, -2));

        row.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                cycle(row, options, apply);
            }
        });
        row.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    cycle(row, options, apply);
                    return true;
                }
                return false;
            }
        });
        row.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean focus) {
                name.setTextColor(focus ? 0xFFFFFFFF : 0xC8FFFFFF);
                value.setTextColor(focus ? 0xFFFFFFFF : 0x8EFFFFFF);
                GradientDrawable hl = new GradientDrawable();
                hl.setColor(focus ? 0x33FFFFFF : Color.TRANSPARENT);
                hl.setCornerRadius(10 * v.getResources().getDisplayMetrics().density);
                v.setBackground(hl);
            }
        });

        list.addView(row, new LinearLayout.LayoutParams(-1, (int) (56 * density)));
        rows.add(row);
        values.add(value);
        currents.add(Math.max(0, Math.min(options.length - 1, current)));
    }

    private void cycle(View row, String[] options, Apply apply) {
        int idx = rowIndex(row);
        if (idx < 0) return;
        int next = (currents.get(idx) + 1) % options.length;
        currents.set(idx, next);
        values.get(idx).setText(options[next]);
        apply.run(next);
        row.requestFocus(); // 重建首页后把焦点还给当前行
    }

    private int rowIndex(View row) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i) == row) return i;
        return -1;
    }

    void reload() {
        currents.set(0, style.rotating() ? 0 : 1);
        values.get(0).setText(ROTATION[style.rotating() ? 0 : 1]);
        int mi = minutesIndex(style.minutes());
        currents.set(1, mi);
        values.get(1).setText(MINUTES[mi]);
        currents.set(2, style.get("size"));
        values.get(2).setText(SIZE[style.get("size")]);
        currents.set(3, style.get("material"));
        values.get(3).setText(MATERIAL[style.get("material")]);
        currents.set(4, style.get("background"));
        values.get(4).setText(BACKGROUND[style.get("background")]);
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
