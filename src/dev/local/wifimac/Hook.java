package dev.local.wifimac;

import android.content.*;
import android.net.MacAddress;
import android.net.wifi.*;
import android.os.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class Hook implements IXposedHookLoadPackage {
    private static final String CMI = "com.android.server.wifi.ClientModeImpl";
    private final List<WeakReference<Object>> clients = new ArrayList<>();
    private volatile boolean installed;
    private boolean installing, registered, busy;
    private volatile SessionRule rule;
    private Handler handler;
    private String last = "尚未设置临时地址";
    private int generation;
    private static void log(String s) { XposedBridge.log("[WifiMacCurrent] " + s); }
    private static Object field(Object o, String n) { return XposedHelpers.getObjectField(o, n); }
    private static Object call(Object o, String n, Object... a) { return XposedHelpers.callMethod(o, n, a); }
    private static boolean primary(Object c) { return Boolean.TRUE.equals(call(c, "isPrimary")); }
    private static String key(WifiConfiguration c) {
        return c.networkId + "|" + call(c, "getProfileKey");
    }
    private static WifiInfo info(Object c) { return (WifiInfo) field(c, "mWifiInfo"); }
    private static WifiConfiguration config(Object c) {
        return (WifiConfiguration) call(c, "getConnectedWifiConfigurationInternal");
    }
    private static String actual(Object c) {
        return String.valueOf(call(field(c, "mWifiNative"), "getMacAddress", field(c, "mInterfaceName")));
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"android".equals(lp.packageName) || !"android".equals(lp.processName)) return;
        log("v0.3.0 system_server entry");
        try {
            // ART can resolve CMI without calling Java ClassLoader.loadClass(String, boolean).
            // Capture the actual Wi-Fi APEX loader before WifiService constructs its clients.
            Class<?> manager = XposedHelpers.findClass("com.android.server.SystemServiceManager", lp.classLoader);
            XposedHelpers.findAndHookMethod(manager, "startService", Class.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        Class<?> service = (Class<?>) p.args[0];
                        if (!installed && service.getName().startsWith("com.android.server.wifi.")) {
                            try {
                                log("Wi-Fi service loader captured: " + service.getName());
                                install(XposedHelpers.findClass(CMI, service.getClassLoader()));
                            } catch (Throwable e) { log("Wi-Fi service capture failed: " + e); }
                        }
                    }
                });
            log("System service startup observer installed");
            Class<?> c = XposedHelpers.findClassIfExists(CMI, lp.classLoader);
            if (c != null) install(c);
        } catch (Throwable e) { log("Initialization failed: " + e); }
    }

    private synchronized void install(Class<?> c) {
        if (installed || installing) return;
        installing = true;
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            // Require the precise AOSP Android 16 connection path; do not guess vendor methods.
            Method randomized = c.getDeclaredMethod("configureRandomizedMacAddress", WifiConfiguration.class);
            Method factory = c.getDeclaredMethod("setCurrentMacToFactoryMac", WifiConfiguration.class);
            Method start = c.getDeclaredMethod("updateWifiConfigOnStartConnection", WifiConfiguration.class, String.class);
            c.getDeclaredMethod("startConnectToNetwork", int.class, int.class, String.class);
            c.getDeclaredMethod("disconnect"); c.getDeclaredMethod("isPrimary");
            c.getDeclaredMethod("getConnectedWifiConfigurationInternal");
            XC_MethodHook replace = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        WifiConfiguration cfg = (WifiConfiguration) p.args[0];
                        SessionRule r = rule;
                        if (r == null || cfg == null || !r.matches(key(cfg), primary(p.thisObject))) return;
                        Object nativeWifi = field(p.thisObject, "mWifiNative");
                        String iface = (String) field(p.thisObject, "mInterfaceName");
                        MacAddress mac = MacAddress.fromString(r.mac);
                        // Only this selected connection is replaced. Disconnect randomization and
                        // every other network's configuration execute the unmodified system path.
                        boolean ok = r.mac.equalsIgnoreCase(actual(p.thisObject));
                        if (!ok) ok = Boolean.TRUE.equals(call(nativeWifi, "setStaMacAddress", iface, mac));
                        if (!ok) {
                            rule = null; last = "驱动拒绝地址，已取消覆盖并回退系统策略";
                            log("HAL rejected; original configuration path retained"); return;
                        }
                        call(nativeWifi, "removeNetworkCachedDataIfNeeded", cfg.networkId, mac);
                        last = "驱动已接受请求，读回 " + actual(p.thisObject) + "；请核对路由器";
                        log("HAL accepted; readback=" + actual(p.thisObject));
                        p.setResult(null);
                    } catch (Throwable e) {
                        rule = null; last = "地址路径不兼容，已取消覆盖：" + e.getClass().getSimpleName();
                        log("Override failed, retaining original method: " + e);
                    }
                }
            };
            hooks.add(XposedBridge.hookMethod(randomized, replace));
            hooks.add(XposedBridge.hookMethod(factory, replace));
            hooks.add(XposedBridge.hookMethod(start, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        SessionRule r = rule; WifiConfiguration cfg = (WifiConfiguration) p.args[0];
                        if (r != null && cfg != null && primary(p.thisObject) && !r.key.equals(key(cfg))) {
                            rule = null; generation++; busy = false;
                            last = "已切换到其他网络，临时覆盖自动清除";
                        }
                    } catch (Throwable e) { rule = null; log("Network guard failed: " + e); }
                }
            }));
            hooks.addAll(XposedBridge.hookAllConstructors(c, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) return;
                    try { attach(p.thisObject); }
                    catch (Throwable e) { log("Control receiver unavailable: " + e); }
                }
            }));
            installed = true; log("Connection hooks installed");
        } catch (Throwable e) {
            for (XC_MethodHook.Unhook hook : hooks) hook.unhook();
            log("Unsupported ROM; hooks rolled back: " + e);
        } finally { installing = false; }
    }

    private synchronized void attach(Object c) {
        clients.removeIf(ref -> ref.get() == null);
        clients.add(new WeakReference<>(c));
        if (registered) return;
        handler = (Handler) call(c, "getHandler");
        Context context = (Context) field(c, "mContext");
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                try { control(this, intent); }
                catch (Throwable e) {
                    Bundle result = new Bundle(); result.putBoolean("ready", true);
                    last = "操作失败：" + e.getClass().getSimpleName() + " / " + e.getMessage();
                    result.putString("message", last);
                    setResult(1, null, result); log("Control error: " + e);
                }
            }
        }, new IntentFilter(Protocol.ACTION), Protocol.PERMISSION, handler, Context.RECEIVER_EXPORTED);
        registered = true; log("Signature-protected control receiver ready");
    }

    private synchronized Object connected() {
        Object selected = null;
        for (WeakReference<Object> ref : clients) {
            Object c = ref.get(); if (c == null) continue;
            try {
                if (primary(c) && info(c).getSupplicantState() == SupplicantState.COMPLETED
                        && info(c).getNetworkId() >= 0 && config(c) != null) selected = c;
            } catch (Throwable ignored) {}
        }
        return selected;
    }

    private void control(BroadcastReceiver receiver, Intent intent) {
        String op = intent.getStringExtra("op");
        Object c = connected();
        if ("apply".equals(op)) {
            if (busy) throw new IllegalStateException("重连进行中");
            if (c == null) throw new IllegalStateException("当前未连接 Wi-Fi");
            WifiConfiguration cfg = config(c);
            if (!key(cfg).equals(intent.getStringExtra("key"))) throw new IllegalStateException("网络已变化，请刷新");
            // Android sets the STA MLD address through the same setStaMacAddress HAL path.
            // Per-link addresses belong to vendor firmware; display them, never spoof them.
            if (cfg.isPasspoint() || cfg.enterpriseConfig != null && cfg.enterpriseConfig.getEapMethod() != -1)
                throw new IllegalStateException("此版本只支持普通个人 Wi-Fi");
            rule = new SessionRule(key(cfg), intent.getStringExtra("mac"));
            last = "已接收请求，正在断开并重连当前 Wi-Fi；尚未确认成功";
            reconnect(c, cfg);
        } else if ("clear".equals(op)) {
            SessionRule previous = rule; rule = null;
            last = "临时覆盖已清除";
            if (!busy && c != null && previous != null && previous.key.equals(key(config(c)))) reconnect(c, config(c));
        } else if (!"query".equals(op)) throw new IllegalArgumentException("未知操作");
        Bundle b = new Bundle(); b.putBoolean("ready", true); b.putBoolean("busy", busy);
        b.putString("message", last); b.putString("target", rule == null ? "" : rule.mac);
        if (c != null) {
            b.putString("key", key(config(c))); b.putString("ssid", config(c).SSID);
            b.putString("actual", actual(c));
            b.putBoolean("mlo", Boolean.TRUE.equals(call(c, "isMlo")));
            StringBuilder links = new StringBuilder();
            try {
                for (Object link : (List<?>) call(info(c), "getAssociatedMloLinks")) {
                    if (links.length() > 0) links.append("\n");
                    links.append("链路 ").append(call(link, "getLinkId")).append(": ")
                        .append(call(link, "getStaMacAddress"));
                }
            } catch (Throwable e) { links.append("当前系统未提供链路地址"); }
            b.putString("links", links.toString());
        }
        receiver.setResult(1, null, b);
    }

    private void reconnect(Object c, WifiConfiguration cfg) {
        final String expected = key(cfg); final int networkId = cfg.networkId;
        final int ticket = ++generation;
        busy = true;
        try { call(c, "disconnect"); }
        catch (Throwable e) { busy = false; rule = null; throw e; }
        final long deadline = SystemClock.elapsedRealtime() + 12000;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (ticket != generation) return;
                try {
                    if (!primary(c)) { cancel("主 Wi-Fi 接口已变化，请重新操作"); return; }
                    Object active = connected();
                    if (active != null && !expected.equals(key(config(active)))) {
                        cancel("已连接其他网络，取消重连和覆盖"); return;
                    }
                    String state = String.valueOf(call(call(c, "getCurrentState"), "getName"));
                    if ("DisconnectedState".equals(state)) {
                        Object manager = field(c, "mWifiConfigManager");
                        WifiConfiguration fresh = (WifiConfiguration) call(manager, "getConfiguredNetwork", networkId);
                        if (fresh == null || !expected.equals(key(fresh))) { cancel("网络配置已变化"); return; }
                        call(c, "startConnectToNetwork", networkId, 1000, "any");
                        // Keep actions gated briefly while the asynchronous connect runs.
                        handler.postDelayed(() -> { if (ticket == generation) busy = false; }, 8000);
                        return;
                    }
                    if (SystemClock.elapsedRealtime() >= deadline) {
                        if (active != null && expected.equals(key(config(active)))) {
                            busy = false;
                            last = "系统已自动重连当前网络；请核对系统读回和路由器地址";
                            return;
                        }
                        cancel("断开等待超时，覆盖已取消；请手动重连当前网络"); return;
                    }
                    handler.postDelayed(this, 300);
                } catch (Throwable e) { cancel("重连路径不兼容，请手动重连：" + e.getClass().getSimpleName()); log("Reconnect failed: " + e); }
            }
        }, 300);
    }
    private void cancel(String message) { rule = null; busy = false; generation++; last = message; }
}
