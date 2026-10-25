package com.appletvstyle.launcher;

/** 轮播间隔归一化（1 / 5 / 15 / 30 分钟，默认 5）。 */
final class WallpaperCycle {
    private WallpaperCycle() {}

    static final int DEFAULT_MINUTES = 5;

    static int next(int current, int count) {
        return count <= 1 ? current : (current + 1) % count;
    }

    static int minutes(int value) {
        return value == 1 || value == 15 || value == 30 ? value : DEFAULT_MINUTES;
    }
}
