package com.appletvstyle.launcher;

import android.content.Context;
import android.graphics.Typeface;

/** Outfit (OFL,  Google Fonts) 静态字重缓存 */
public final class Fonts {
    private Fonts() {}
    private static Typeface thin, light, regular, medium, semibold;

    public static Typeface thin(Context c) {
        if (thin == null) thin = Typeface.createFromAsset(c.getAssets(), "fonts/Outfit-Thin.ttf");
        return thin;
    }
    public static Typeface light(Context c) {
        if (light == null) light = Typeface.createFromAsset(c.getAssets(), "fonts/Outfit-Light.ttf");
        return light;
    }
    public static Typeface regular(Context c) {
        if (regular == null) regular = Typeface.createFromAsset(c.getAssets(), "fonts/Outfit-Regular.ttf");
        return regular;
    }
    public static Typeface medium(Context c) {
        if (medium == null) medium = Typeface.createFromAsset(c.getAssets(), "fonts/Outfit-Medium.ttf");
        return medium;
    }
    public static Typeface semibold(Context c) {
        if (semibold == null) semibold = Typeface.createFromAsset(c.getAssets(), "fonts/Outfit-SemiBold.ttf");
        return semibold;
    }
}
