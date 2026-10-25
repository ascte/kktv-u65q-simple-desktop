package com.appletvstyle.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 首页入口：默认项（按 KKTV 实际预装过滤）+ 运行时发现 + 用户排序（最多 9 项）。
 * 参考归藏电视 HomeItems 的身份模型：移除入口不会卸载应用。
 */
final class HomeItems {
    /** KKTV U65Q 默认入口（包里没有的会在运行时过滤掉）。 */
    static final String[] DEFAULTS = {
            "com.xiaodianshi.tv.yst",   // 哔哩哔哩（云视听小电视）
            "com.quark.yun.tv",         // 夸克网盘
            "com.dangbeimarket",        // 当贝市场
            "com.konka.kkmultiscreen",  // 多屏互动
            "com.konka.multimedia",     // 康佳多媒体
            "com.konka.systemsetting",  // 系统设置
    };
    private static final String[] DEFAULT_LABELS = {
            "哔哩哔哩", "夸克网盘", "当贝市场", "多屏互动", "康佳多媒体", "设置",
    };

    private final Context context;
    final LinkedHashMap<String, String> names = new LinkedHashMap<>();
    final List<String> order = new ArrayList<>();

    HomeItems(Context c) {
        context = c;
        for (int i = 0; i < DEFAULTS.length; i++) names.put(DEFAULTS[i], DEFAULT_LABELS[i]);
        reload();
    }

    /**
     * 重新扫描已安装应用并重算首页顺序；顺序发生变化时返回 true。
     *
     * 必须可重复调用：康佳固件在系统时间错误时（重启后停在 1970 的窗口期）
     * PMS 会查不到当天新装的应用（B 站/夸克的时间戳记在 1970 时间基上），
     * 时间同步成功后要重查一次才能把它们补回列表。
     */
    boolean reload() {
        List<String> before = new ArrayList<>(order);
        discover();
        order.clear();
        try {
            JSONArray data = new JSONArray(context.getSharedPreferences("home-items", 0).getString("order", "[]"));
            for (int i = 0; i < data.length() && order.size() < 9; i++) {
                String id = data.getString(i);
                if (validId(id) && !order.contains(id) && installed(id)) order.add(id);
            }
        } catch (Exception ignored) { order.clear(); }
        if (order.isEmpty()) {
            for (String id : DEFAULTS) if (installed(id)) order.add(id);
        }
        if (order.isEmpty()) order.add("com.dangbeimarket");
        return !before.equals(order);
    }

    /** 扫描已安装的 LAUNCHER / LEANBACK_LAUNCHER 活动，补全名称表。 */
    void discover() {
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : context.getPackageManager()
                    .queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String id = r.activityInfo.packageName;
                if (!id.equals(context.getPackageName()) && r.activityInfo.enabled && r.activityInfo.exported) {
                    String label = r.loadLabel(context.getPackageManager()).toString();
                    if (!names.containsKey(id)) names.put(id, label);
                }
            }
        }
    }

    private boolean installed(String id) {
        return context.getPackageManager().getLaunchIntentForPackage(id) != null
                || context.getPackageManager().getLeanbackLaunchIntentForPackage(id) != null;
    }

    String name(String id) {
        String n = names.get(id);
        if (n == null) n = context.getSharedPreferences("home-items", 0).getString("name:" + id, id);
        return n;
    }
    static boolean validId(String id) {
        return id.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+");
    }

    boolean save(List<String> next) {
        if (next.isEmpty() || next.size() > 9 || new java.util.HashSet<>(next).size() != next.size()) return false;
        for (String id : next) if (!validId(id)) return false;
        android.content.SharedPreferences.Editor e = context.getSharedPreferences("home-items", 0).edit()
                .putString("order", new JSONArray(next).toString());
        for (String id : next) e.putString("name:" + id, name(id));
        if (!e.commit()) return false;
        order.clear();
        order.addAll(next);
        return true;
    }

    /** 全部可启动应用（排除自己），供「全部应用」面板使用，按中文名排序。 */
    List<String> launchable() {
        List<String> ids = new ArrayList<>(names.keySet());
        java.util.Collections.sort(ids, (a, b) -> java.text.Collator.getInstance(java.util.Locale.CHINA)
                .compare(name(a), name(b)));
        return ids;
    }
}
