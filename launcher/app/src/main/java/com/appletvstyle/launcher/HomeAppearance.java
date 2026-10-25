package com.appletvstyle.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 外观偏好（参考归藏电视 HomeAppearance，默认值保留「照片 + 细体大字列表」原始风格）。
 * size 文字大小 0/1/2 → 32/38/42sp；dim 未聚焦透明度 0/1/2 → .75/.55/.35；
 * material 毛玻璃 0/1/2 → 轻/中/重；background 0=照片壁纸 1=纯黑。
 */
final class HomeAppearance {
    private final SharedPreferences prefs;

    HomeAppearance(Context c) {
        prefs = c.getSharedPreferences("home-appearance", Context.MODE_PRIVATE);
    }

    int get(String key) {
        return prefs.getInt(key, defaultValue(key));
    }

    boolean set(String key, int value) {
        return prefs.edit().putInt(key, value).commit();
    }

    private static int defaultValue(String key) {
        if ("size".equals(key) || "dim".equals(key)) return 1;
        return 0;
    }

    boolean photoWallpaper() { return get("background") == 0; }

    int ink() { return 0xFFFFFFFF; }

    float faded() { return new float[]{.75f, .55f, .35f}[get("dim")]; }

    float size() { return new float[]{32, 38, 42}[get("size")]; }

    int blur() { return new int[]{14, 42, 2}[get("material")]; }

    int veil() { return new int[]{0x60101418, 0x48101418, 0x30101418}[get("material")]; }

    boolean rotating() { return prefs.getBoolean("rotating", true); }

    int minutes() { return prefs.getInt("minutes", 5); }

    boolean setRotation(boolean enabled, int value) {
        return prefs.edit().putBoolean("rotating", enabled).putInt("minutes", value).commit();
    }
}
