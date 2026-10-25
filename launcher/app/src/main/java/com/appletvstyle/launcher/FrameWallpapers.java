package com.appletvstyle.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 内置壁纸：有界解码、记住选择、仅前台轮播（参考归藏电视 FrameWallpapers 移植）。
 * 交叉淡入切换；离开桌面暂停计时，返回后重新计时。
 */
final class FrameWallpapers {
    private static final class Entry {
        final String id, name, file;

        Entry(String id, String name, String file) {
            this.id = id;
            this.name = name;
            this.file = file;
        }
    }

    private final Activity activity;
    private final ImageView photo, transition;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Entry> entries = new ArrayList<>();
    private boolean rotating, active, loading, destroyed, hasImage;
    private int current, minutes, generation;
    Runnable changed = new Runnable() {
        @Override
        public void run() {}
    };
    private final Runnable rotate = new Runnable() {
        @Override
        public void run() {
            load(WallpaperCycle.next(current, entries.size()));
        }
    };

    FrameWallpapers(Activity activity, ImageView photo, ImageView transition, HomeAppearance style) {
        this.activity = activity;
        this.photo = photo;
        this.transition = transition;
        prefs = activity.getSharedPreferences("frame-wallpapers", Activity.MODE_PRIVATE);
        rotating = style.rotating();
        minutes = WallpaperCycle.minutes(style.minutes());
        try (InputStream stream = activity.getAssets().open("wallpapers/catalog.json")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = stream.read(buffer)) != -1) bytes.write(buffer, 0, n);
            JSONArray json = new JSONArray(bytes.toString("UTF-8"));
            if (json.length() > 15) throw new IllegalArgumentException("At most 15 wallpapers");
            for (int i = 0; i < json.length(); i++) {
                JSONObject o = json.getJSONObject(i);
                entries.add(new Entry(o.getString("id"), o.getString("name"), o.getString("file")));
            }
        } catch (Exception error) {
            android.util.Log.e("LC", "wallpaper catalog unavailable", error);
        }
        if (entries.isEmpty()) {
            String[] names = {"暮色", "松林", "暖砂", "深海", "紫夜"};
            for (int i = 0; i < names.length; i++) {
                entries.add(new Entry("builtin-" + i, names[i], "builtin:" + i));
            }
        }
        String id = prefs.getString("selected", "");
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(id)) current = i;
        }
        load(current);
    }

    private Bitmap decode(String file) throws Exception {
        if (file.startsWith("builtin:")) {
            int[][] palettes = {{0xFF172B3B, 0xFFB78060}, {0xFF152C28, 0xFF6D8B68},
                    {0xFF3C302B, 0xFFC6A376}, {0xFF0B2238, 0xFF397E92}, {0xFF24203A, 0xFF866B8D}};
            int index = Integer.parseInt(file.substring(8));
            Bitmap image = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(image);
            Paint paint = new Paint();
            paint.setShader(new LinearGradient(0, image.getHeight(), image.getWidth(), 0,
                    palettes[index], null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, image.getWidth(), image.getHeight(), paint);
            return image;
        }
        try (InputStream stream = activity.getAssets().open("wallpapers/" + file)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap image = BitmapFactory.decodeStream(stream, null, options);
            if (image == null) throw new java.io.IOException("Image unavailable");
            return image;
        }
    }

    private void load(final int index) {
        load(index, null);
    }

    private void load(final int index, final Runnable success) {
        if (destroyed || entries.isEmpty()) return;
        handler.removeCallbacks(rotate);
        final int request = ++generation;
        loading = true;
        worker.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = null;
                try {
                    bitmap = decode(entries.get(index).file);
                } catch (Exception error) {
                    android.util.Log.e("LC", "wallpaper decode failed", error);
                }
                final Bitmap image = bitmap;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (destroyed || request != generation) {
                            if (image != null) image.recycle();
                            return;
                        }
                        loading = false;
                        if (image == null) {
                            schedule();
                            return;
                        }
                        if (!prefs.edit().putString("selected", entries.get(index).id).commit()) {
                            image.recycle();
                            schedule();
                            return;
                        }
                        transition.animate().cancel();
                        if (transition.getDrawable() != null) {
                            photo.setImageDrawable(transition.getDrawable());
                        }
                        transition.setImageDrawable(null);
                        if (!hasImage || !active) {
                            photo.setImageBitmap(image);
                            changed.run();
                        } else {
                            transition.setImageBitmap(image);
                            transition.setAlpha(0f);
                            transition.animate().alpha(1f).setDuration(700).withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    photo.setImageBitmap(image);
                                    changed.run();
                                    transition.setImageDrawable(null);
                                    transition.setAlpha(0f);
                                }
                            }).start();
                        }
                        hasImage = true;
                        current = index;
                        if (success != null) success.run();
                        schedule();
                    }
                });
            }
        });
    }

    private void schedule() {
        handler.removeCallbacks(rotate);
        if (active && !destroyed && !loading && rotating && entries.size() > 1) {
            handler.postDelayed(rotate, minutes * 60000L);
        }
    }

    void resume() {
        active = true;
        schedule();
    }

    void pause() {
        active = false;
        handler.removeCallbacks(rotate);
    }

    void destroy() {
        destroyed = true;
        generation++;
        pause();
        transition.animate().cancel();
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
    }

    int count() {
        return entries.size();
    }

    int selected() {
        return current;
    }

    String name(int index) {
        return entries.get(index).name;
    }

    void select(int index, Runnable success) {
        load(index, success);
    }

    boolean rotation(boolean enabled, int value) {
        int next = WallpaperCycle.minutes(value);
        if (!prefs.edit().putBoolean("rotating", enabled).putInt("minutes", next).commit()) return false;
        rotating = enabled;
        minutes = next;
        schedule();
        return true;
    }

    void applyStyle(HomeAppearance style) {
        rotating = style.rotating();
        minutes = WallpaperCycle.minutes(style.minutes());
        schedule();
    }
}
