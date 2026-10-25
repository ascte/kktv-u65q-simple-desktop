package com.appletvstyle.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 归藏电视风格首页宿主：照片壁纸 + 交叉淡入轮播 + 细体大字列表 + 毛玻璃焦点。
 * 时钟按整分钟对齐刷新（旧版 15s tick + 12 小时制无上下午标识，是「时间不同步」观感的来源）。
 */
public final class HomeActivity extends Activity implements HomeSurface.Actions {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private HomeAppearance appearance;
    private HomeItems items;
    private FrameWallpapers wallpapers;
    private HomeSurface surface;
    private FrameLayout root;
    private ImageView photo, transition;
    private AppsPanel appsPanel;
    private SettingsPanel settingsPanel;
    private InputsPopup inputsPopup;
    private boolean resumed;
    private int selected;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            String time = new SimpleDateFormat("HH:mm", Locale.CHINA)
                    .format(new Date(NetworkTime.now()));
            if (surface != null) surface.clock.setText(time);
            handler.postDelayed(this, 60000 - NetworkTime.now() % 60000);
        }
    };

    /** 每 15 分钟重对一次网络时间（电视固件的系统 NTP 是坏的）。 */
    private final Runnable resync = new Runnable() {
        @Override
        public void run() {
            NetworkTime.syncAsync(HomeActivity.this);
            handler.postDelayed(this, 15 * 60000L);
        }
    };

    /**
     * 开机后延迟兜底重查应用列表。
     * 康佳固件在系统时间错误时（重启后停在 1970 的窗口期）PMS 查不到当天
     * 新装的应用（B 站/夸克的时间戳记在 1970 时间基上），可能要等几秒甚至
     * 对时成功后才恢复暴露——不依赖任何单一触发点，多刷几次保证补齐。
     */
    private final Runnable lateRefresh = new Runnable() {
        @Override
        public void run() {
            if (resumed && items.reload()) refreshHome();
        }
    };

    /** 装 / 卸 / 更新应用后实时刷新首页列表。 */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (items.reload()) {
                refreshHome();
                if (appsPanel != null && appsPanel.getParent() != null) appsPanel.refresh(items);
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        appearance = new HomeAppearance(this);
        items = new HomeItems(this);
        selected = getSharedPreferences("frame-home", 0).getInt("selected", 0);
        root = new FrameLayout(this);
        photo = new ImageView(this);
        transition = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        transition.setScaleType(ImageView.ScaleType.CENTER_CROP);
        transition.setAlpha(0);
        root.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        root.addView(transition, new FrameLayout.LayoutParams(-1, -1));
        wallpapers = new FrameWallpapers(this, photo, transition, appearance);
        wallpapers.changed = new Runnable() {
            @Override
            public void run() {
                if (surface != null) surface.wallpaper(wallpaperDrawable());
            }
        };
        // 对时成功 → 立即重绘时钟，并重查应用列表把开机窗口期漏掉的入口补回来
        NetworkTime.onSynced = new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(tick);
                tick.run();
                // 系统时间刚从 1970 纠正过来，此时 PMS 才查得到当天新装的应用
                if (resumed && items.reload()) refreshHome();
            }
        };
        setContentView(root);
        refreshHome();
        // 开机窗口期兜底：时间没对上之前列表可能不全，延迟多查几次
        handler.postDelayed(lateRefresh, 3000);
        handler.postDelayed(lateRefresh, 9000);
        handler.postDelayed(lateRefresh, 20000);
    }

    void refreshHome() {
        int restore = Math.max(0, Math.min(items.order.size() - 1, selected));
        selected = restore;
        if (surface != null) root.removeView(surface);
        root.setBackgroundColor(appearance.photoWallpaper() ? 0xFF11151B : 0xFF000000);
        photo.setVisibility(appearance.photoWallpaper() ? View.VISIBLE : View.INVISIBLE);
        transition.setVisibility(photo.getVisibility());
        surface = new HomeSurface(this, appearance, items, wallpaperDrawable(), restore, this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        boolean panelOpen = (settingsPanel != null && settingsPanel.showing())
                || (appsPanel != null && appsPanel.showing())
                || (inputsPopup != null && inputsPopup.showing());
        if (!panelOpen && !surface.apps.isEmpty()) surface.apps.get(restore).requestFocus();
        selected = restore;
        handler.removeCallbacks(tick);
        tick.run();
        updateRotation();
    }

    Drawable wallpaperDrawable() {
        return photo.getDrawable();
    }

    // ---------------- HomeSurface.Actions ----------------

    @Override
    public void open(String id) {
        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(id);
        if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(id);
        launch(intent, "应用暂时不可用");
    }

    @Override
    public void control(int index) {
        if (index == 0) {
            showAppsPanel();
        } else if (index == 1) {
            showInputs();
        } else if (index == 2) {
            showSettings();
        }
    }

    @Override
    public void selected(int index) {
        selected = index;
        getSharedPreferences("frame-home", 0).edit().putInt("selected", index).apply();
    }

    // ---------------- 面板 ----------------

    private void showAppsPanel() {
        if (appsPanel == null) {
            appsPanel = new AppsPanel(this, items);
            appsPanel.actions = new AppsPanel.Actions() {
                @Override
                public void open(String id) {
                    Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(id);
                    if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(id);
                    launch(intent, "应用暂时不可用");
                }

                @Override
                public void dismissed() {
                    updateRotation();
                }
            };
        } else {
            appsPanel.refresh(items);
        }
        if (appsPanel.getParent() == null) {
            root.addView(appsPanel, new FrameLayout.LayoutParams(-1, -1));
        }
        appsPanel.show();
        updateRotation();
    }

    private void showInputs() {
        if (inputsPopup == null) {
            inputsPopup = new InputsPopup(this);
            inputsPopup.onDismiss = new Runnable() {
                @Override
                public void run() {
                    updateRotation();
                }
            };
        }
        if (inputsPopup.getParent() == null) {
            root.addView(inputsPopup, new FrameLayout.LayoutParams(-2, -2));
        }
        inputsPopup.show();
        updateRotation();
    }

    private void showSettings() {
        if (settingsPanel == null) {
            settingsPanel = new SettingsPanel(this, appearance);
            settingsPanel.actions = new SettingsPanel.Actions() {
                @Override
                public void changed() {
                    refreshHome();
                }

                @Override
                public void rotationChanged() {
                    wallpapers.applyStyle(appearance);
                    updateRotation();
                }

                @Override
                public void dismissed() {
                    updateRotation();
                }
            };
        } else {
            settingsPanel.reload();
        }
        if (settingsPanel.getParent() == null) {
            root.addView(settingsPanel, new FrameLayout.LayoutParams(-1, -1));
        }
        settingsPanel.show();
        updateRotation();
    }

    // ---------------- 生命周期 ----------------

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        // 回到桌面时重查一次列表（此前 PMS 可能因系统时间错误漏掉了新装应用）
        if (items.reload()) refreshHome();
        handler.removeCallbacks(tick);
        tick.run();
        handler.removeCallbacks(resync);
        NetworkTime.syncAsync(this);
        handler.postDelayed(resync, 15 * 60000L);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
        updateRotation();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
    }

    @Override
    protected void onPause() {
        resumed = false;
        try {
            unregisterReceiver(packageReceiver);
        } catch (IllegalArgumentException ignored) { }
        handler.removeCallbacks(tick);
        handler.removeCallbacks(resync);
        wallpapers.pause();
        dismissPanels();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        NetworkTime.onSynced = null;
        wallpapers.destroy();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (settingsPanel != null && settingsPanel.showing()) {
            settingsPanel.hide();
        } else if (appsPanel != null && appsPanel.showing()) {
            appsPanel.hide();
        } else if (inputsPopup != null && inputsPopup.showing()) {
            inputsPopup.hide();
        } else if (surface != null && surface.focusedControl() != null) {
            surface.focusApp();
        } else {
            super.onBackPressed();
        }
    }

    private void dismissPanels() {
        if (settingsPanel != null && settingsPanel.showing()) settingsPanel.hide();
        if (appsPanel != null && appsPanel.showing()) appsPanel.hide();
        if (inputsPopup != null && inputsPopup.showing()) inputsPopup.hide();
    }

    void updateRotation() {
        boolean clear = resumed && appearance.photoWallpaper()
                && !(settingsPanel != null && settingsPanel.showing())
                && !(appsPanel != null && appsPanel.showing())
                && !(inputsPopup != null && inputsPopup.showing());
        if (clear) {
            wallpapers.resume();
        } else {
            wallpapers.pause();
        }
    }

    private void launch(Intent i, String error) {
        if (i == null) {
            message(error);
            return;
        }
        try {
            startActivity(i);
        } catch (RuntimeException unavailable) {
            message(error);
        }
    }

    void message(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
