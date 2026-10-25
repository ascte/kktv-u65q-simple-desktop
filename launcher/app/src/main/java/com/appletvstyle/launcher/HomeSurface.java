package com.appletvstyle.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 归藏电视风格首页：照片壁纸上的细体大字纵向列表 + 右下操作区 + 右上时钟，
 * 焦点用按内容宽度包裹的壁纸毛玻璃高亮（参考 HomeSurface 移植，KKTV 适配）。
 */
final class HomeSurface extends FrameLayout {
    interface Actions {
        void open(String id);

        void control(int index);

        void selected(int index);
    }

    final List<View> apps = new ArrayList<>(), controls = new ArrayList<>();
    final TextView clock;
    private final HomeAppearance style;
    private final HomeItems items;
    private final Actions actions;
    private int selected;
    private android.graphics.Bitmap material;
    private int materialRequest;

    HomeSurface(Context c, HomeAppearance style, HomeItems items, Drawable wallpaper,
                int selectedIndex, Actions handler) {
        super(c);
        this.style = style;
        this.items = items;
        this.actions = handler;
        this.selected = Math.max(0, Math.min(items.order.size() - 1, selectedIndex));

        if (style.photoWallpaper()) {
            View shade = new View(c);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x990C0C0C, 0x360C0C0C, 0x700C0C0C}));
            addView(shade, new LayoutParams(-1, -1));
        }

        // ---- 左中大字列表 ----
        final LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        LayoutParams p = new LayoutParams(dp(620), dp(400), Gravity.CENTER_VERTICAL | Gravity.START);
        p.leftMargin = dp(56);
        addView(scroll, p);

        for (int i = 0; i < items.order.size(); i++) {
            final String id = items.order.get(i);
            final int index = i;
            LinearLayout tile = new LinearLayout(c);
            tile.setOrientation(LinearLayout.HORIZONTAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(dp(14), dp(4), dp(18), dp(4));
            TextView label = text(items.name(id), style.size());
            label.setTypeface(Fonts.light(c));
            label.setMaxWidth(dp(510));
            label.setMaxLines(1);
            label.setEllipsize(TextUtils.TruncateAt.END);
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(HomeIcons.get(c, id, style.ink()));
            tile.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = dp(16);
            tile.addView(label, lp);
            LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-2, dp(65));
            list.addView(tile, row);
            apps.add(tile);
            wire(tile, new Runnable() {
                @Override
                public void run() {
                    HomeSurface.this.actions.open(id);
                }
            });
            tile.setContentDescription(items.name(id));
            boolean chosen = index == this.selected;
            paint(tile, chosen);
            tile.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean focus) {
                    paint(tile, focus);
                    if (focus) {
                        HomeSurface.this.selected = index;
                        HomeSurface.this.actions.selected(index);
                        for (int ci = 0; ci < controls.size(); ci++) {
                            controls.get(ci).setNextFocusLeftId(tile.getId());
                        }
                    }
                }
            });
        }

        // ---- 右上时钟 ----
        clock = text("", 30);
        clock.setTypeface(Fonts.thin(c));
        clock.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams cp = new LayoutParams(-2, dp(45), Gravity.TOP | Gravity.END);
        cp.topMargin = dp(45);
        cp.rightMargin = dp(64);
        addView(clock, cp);

        // ---- 右下操作区 ----
        LinearLayout side = new LinearLayout(c);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.END);
        LayoutParams sp = new LayoutParams(dp(180), -2, Gravity.BOTTOM | Gravity.END);
        sp.rightMargin = dp(64);
        sp.bottomMargin = dp(46);
        addView(side, sp);
        String[] names = {"全部应用", "其他信号源", "设置"};
        String[] ids = {"apps", "inputs", "settings"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout line = new LinearLayout(c);
            line.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            TextView label = text(names[i], 16);
            label.setTypeface(Fonts.light(c));
            line.addView(label, new LinearLayout.LayoutParams(-2, -2));
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(HomeIcons.get(c, "control:" + ids[i], style.ink()));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(23), dp(23));
            ip.leftMargin = dp(12);
            line.addView(icon, ip);
            line.setPadding(dp(12), 0, dp(12), 0);
            side.addView(line, new LinearLayout.LayoutParams(-2, dp(40)));
            controls.add(line);
            wire(line, new Runnable() {
                @Override
                public void run() {
                    HomeSurface.this.actions.control(index);
                }
            });
            line.setContentDescription(names[index]);
            paint(line, false);
            line.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean focus) {
                    paint(line, focus);
                }
            });
        }

        // ---- D-pad 确定性导航 ----
        for (int i = 0; i < apps.size(); i++) {
            View v = apps.get(i);
            View prev = apps.get(Math.max(0, i - 1));
            View next = apps.get(Math.min(apps.size() - 1, i + 1));
            v.setNextFocusUpId(prev.getId());
            v.setNextFocusDownId(next.getId());
            v.setNextFocusLeftId(v.getId());
            v.setNextFocusRightId(controls.get(0).getId());
        }
        for (int i = 0; i < controls.size(); i++) {
            View v = controls.get(i);
            v.setNextFocusUpId(controls.get(Math.max(0, i - 1)).getId());
            v.setNextFocusDownId(controls.get(Math.min(controls.size() - 1, i + 1)).getId());
            v.setNextFocusRightId(v.getId());
            v.setNextFocusLeftId(apps.get(this.selected).getId());
        }

        wallpaper(wallpaper);
    }

    /** 毛玻璃焦点：未聚焦淡化，聚焦时贴上按宽度包裹的壁纸模糊。 */
    private void paint(View v, boolean focused) {
        v.setAlpha(focused ? 1f : style.faded());
        if (style.photoWallpaper() && focused) {
            v.setBackground(new WallpaperMaterial.Glass(this, v, material,
                    dp(12), style.veil()));
        } else {
            v.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    void wallpaper(Drawable drawable) {
        if (!style.photoWallpaper()) return;
        final int request = ++materialRequest;
        WallpaperMaterial.request(drawable, style.blur(), new WallpaperMaterial.Ready() {
            @Override
            public void accept(android.graphics.Bitmap field) {
                if (request != materialRequest) return;
                material = field;
                for (View v : apps) {
                    if (v.isFocused()) paint(v, true);
                }
                for (View v : controls) {
                    if (v.isFocused()) paint(v, true);
                }
            }
        });
    }

    void focusApp() {
        if (!apps.isEmpty()) apps.get(selected).requestFocus();
    }

    View focusedControl() {
        for (View v : controls) if (v.isFocused()) return v;
        return null;
    }

    private void wire(View v, Runnable run) {
        v.setId(View.generateViewId());
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        v.setClickable(true);
        v.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View w) {
                run.run();
            }
        });
    }

    private TextView text(String s, float size) {
        TextView v = new TextView(getContext());
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(style.ink());
        v.setIncludeFontPadding(false);
        return v;
    }

    private int dp(float n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}
