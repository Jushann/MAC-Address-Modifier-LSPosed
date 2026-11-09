package dev.local.wifimac;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** App-only appearance preferences; never shared with the Wi-Fi hook. */
final class ThemePalette {
    static final String[] MODES = {"跟随系统", "浅色", "深色"};
    static final String[] NAMES = {"靛蓝", "海蓝", "青绿", "紫罗兰", "玫瑰"};
    private static final int[] LIGHT = {0xff4f5f8b,0xff236594,0xff256c60,0xff755299,0xff934c6b};
    private static final int[] DARK = {0xffb6c5f5,0xff99cefa,0xff93d6c5,0xffd8b9fa,0xfff3b4d0};
    final int mode, color;
    final boolean dark;
    final int background, surface, ink, muted, accent, onAccent, secondary, divider, disabled, ripple;

    ThemePalette(Context context) {
        SharedPreferences p = prefs(context);
        mode = clamp(p.getInt("mode", 0), MODES.length);
        color = clamp(p.getInt("color", 0), NAMES.length);
        dark = mode == 2 || (mode == 0 &&
            (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
        accent = swatch(color);
        onAccent = dark ? 0xff18202c : 0xffffffff;
        background = dark ? 0xff131318 : 0xfffaf8ff;
        surface = dark ? 0xff22222b : 0xffeeedf5;
        ink = dark ? 0xfff0eff5 : 0xff27272d;
        muted = dark ? 0xffb8b6c5 : 0xff656571;
        secondary = blend(surface, accent, dark ? .15f : .12f);
        divider = dark ? 0xff454450 : 0xffd2d1dc;
        disabled = dark ? 0xff94929f : 0xff777783;
        ripple = dark ? 0x33ffffff : 0x22000000;
    }
    int swatch(int index) { return (dark ? DARK : LIGHT)[index]; }
    static void save(Context context, int mode, int color) {
        prefs(context).edit().putInt("mode", mode).putInt("color", color).apply();
    }
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("appearance", Context.MODE_PRIVATE);
    }
    private static int clamp(int n, int count) { return n >= 0 && n < count ? n : 0; }
    private static int blend(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1-f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1-f) + ((b >> 8) & 255) * f);
        int blue = Math.round((a & 255) * (1-f) + (b & 255) * f);
        return 0xff000000 | (r << 16) | (g << 8) | blue;
    }
}
