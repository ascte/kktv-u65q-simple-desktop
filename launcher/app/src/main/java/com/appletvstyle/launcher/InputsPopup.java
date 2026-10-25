package com.appletvstyle.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 输入源下拉: 暗色圆角小面板, 列出 HDMI/AV/电视 输入。
 * 本机没有标准 TV Input Framework (无 tv_input 服务), 选中后广播康佳的信源页
 * action, 由固件自己的选择器完成切换 (README 有说明)。
 */
public class InputsPopup extends LinearLayout {
    private static final String[] ITEMS = {
            "HDMI 1", "HDMI 2", "HDMI 3", "AV", "数字电视", "模拟电视", "轮播"
    };
    private static final String SOURCE_PAGE_ACTION =
            "com.konka.tvsettings.action.GO_TO_SOURCEPAGE";

    private final List<TextView> rows = new ArrayList<>();
    private int index = -1;
    Runnable onDismiss;
    private final Context context;

    public InputsPopup(Context c) {
        super(c);
        context = c;
        init(c);
    }

    public InputsPopup(Context c, AttributeSet attrs) {
        super(c, attrs);
        context = c;
        init(c);
    }

    private void init(Context c) {
        setOrientation(VERTICAL);
        float density = c.getResources().getDisplayMetrics().density;
        int pad = (int) (10 * density);
        setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(0xF0101418);
        bg.setCornerRadius(16 * density);
        bg.setStroke((int) (1 * density), 0x2EFFFFFF);
        setBackground(bg);

        for (int i = 0; i < ITEMS.length; i++) {
            TextView tv = new TextView(c);
            tv.setText(ITEMS[i]);
            tv.setTextSize(18);
            tv.setTextColor(0xD8FFFFFF);
            tv.setTypeface(Fonts.light(c));
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setFocusable(true);
            int hpad = (int) (20 * density);
            int vpad = (int) (10 * density);
            tv.setPadding(hpad, vpad, (int) (36 * density), vpad);
            final int idx = i;
            tv.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(idx);
                }
            });
            tv.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean focus) {
                    tv.setTextColor(focus ? 0xFFFFFFFF : 0xC0FFFFFF);
                    GradientDrawable hl = new GradientDrawable();
                    hl.setColor(focus ? 0x33FFFFFF : Color.TRANSPARENT);
                    hl.setCornerRadius(10 * v.getResources().getDisplayMetrics().density);
                    tv.setBackground(hl);
                }
            });
            rows.add(tv);
            addView(tv);
        }
        setVisibility(GONE);
    }

    private void select(int i) {
        index = i;
        context.sendBroadcast(new Intent(SOURCE_PAGE_ACTION));
        hide();
    }

    public boolean showing() {
        return getVisibility() == VISIBLE;
    }

    public void show() {
        setVisibility(VISIBLE);
        if (!rows.isEmpty()) rows.get(0).requestFocus();
    }

    public void hide() {
        setVisibility(GONE);
        if (onDismiss != null) onDismiss.run();
    }
}
