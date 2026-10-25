package com.appletvstyle.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;

/**
 * 单色图标标记（参考归藏电视 HomeIcons）：固定入口用矢量标记，其余应用用通用窗口标记，
 * 统一以墨色染色，保持列表的克制风格。
 */
final class HomeIcons {
    private HomeIcons() {}

    static Drawable get(Context context, String id, int color) {
        int resource;
        switch (id) {
            case "com.xiaodianshi.tv.yst":
                resource = R.drawable.mark_bilibili;
                break;
            case "com.quark.yun.tv":
                resource = R.drawable.mark_disk;
                break;
            case "com.konka.kkmultiscreen":
                resource = R.drawable.mark_broadcast;
                break;
            case "com.konka.systemsetting":
                resource = R.drawable.mark_gear_six;
                break;
            case "control:apps":
                resource = R.drawable.mark_squares_four;
                break;
            case "control:inputs":
                resource = R.drawable.mark_monitor_play;
                break;
            case "control:settings":
                resource = R.drawable.mark_gear_six;
                break;
            default:
                resource = R.drawable.mark_app_window;
                break;
        }
        Drawable mark = context.getDrawable(resource).mutate();
        mark.setTint(color);
        return mark;
    }
}
