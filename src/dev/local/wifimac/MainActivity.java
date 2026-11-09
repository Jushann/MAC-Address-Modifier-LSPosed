package dev.local.wifimac;
import android.app.Activity;
import android.os.*;
import android.content.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.View;
import android.view.Gravity;

public final class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView connection, status, currentMac, currentLabel, currentNetwork, serviceState;
    private EditText mac;
    private Button apply, clear, copy;
    private String key = "", actualMac = "";
    private boolean visible, inFlight;
    private ThemePalette palette;
    private int BG, SURFACE, INK, MUTED, ACCENT, selectedPage;
    private ScrollView addressPage, settingsPage, detailsPage;
    private TextView addressTab, settingsTab, detailsTab;
    private WifiDetailsView details;
    private final Runnable poll = new Runnable() {
        @Override public void run() { if (visible) { request("query"); if(detailsPage.getVisibility()==View.VISIBLE)details.refresh(); ui.postDelayed(this, 3000); } }
    };
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView label(String text, int size, int color) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(3), 1); return t;
    }
    private void gap(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void section(LinearLayout parent, String name) {
        TextView t = label(name, 16, ACCENT); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(dp(10), dp(22), 0, dp(10)); parent.addView(t);
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = column(); c.setPadding(dp(18), dp(18), dp(18), dp(18));
        c.setBackground(shape(SURFACE, 22)); parent.addView(c, new LinearLayout.LayoutParams(-1, -2)); return c;
    }
    private void divider(LinearLayout parent) {
        View v = new View(this); v.setBackgroundColor(palette.divider);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1));
        p.topMargin = dp(17); p.bottomMargin = dp(17); parent.addView(v, p);
    }
    private Button button(String text, boolean filled) {
        Button b = new Button(this); b.setText(text); b.setTextSize(15); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48));
        b.setPadding(dp(18), dp(10), dp(18), dp(10)); b.setStateListAnimator(null);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
            new int[]{palette.disabled, filled ? palette.onAccent : ACCENT}));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.ripple),
            shape(filled ? ACCENT : palette.secondary, 18), null)); return b;
    }
    private void enabled(Button b, boolean value) { b.setEnabled(value); b.setAlpha(value ? 1f : .50f); }
    private void selectPage(int page) {
        selectedPage = page;
        ScrollView[] views={addressPage,detailsPage,settingsPage}; TextView[] tabs={addressTab,detailsTab,settingsTab};
        for(int i=0;i<3;i++){views[i].setVisibility(i==page?View.VISIBLE:View.GONE);tabs[i].setTextColor(i==page?ACCENT:MUTED);
            tabs[i].setBackground(shape(i==page?palette.secondary:BG,22));tabs[i].setSelected(i==page);}
        if(page==1)details.refresh();
    }
    private void heading(LinearLayout page, String title, String subtitle) {
        gap(page, 30);
        TextView h = label(title, 34, INK); h.setPadding(dp(10), 0, 0, 0); page.addView(h);
        TextView sub = label(subtitle, 15, MUTED); sub.setPadding(dp(10), dp(4), 0, 0); page.addView(sub);
    }
    @Override public void onCreate(Bundle state) {
        palette = new ThemePalette(this);
        setTheme(palette.dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        BG=palette.background; SURFACE=palette.surface; INK=palette.ink; MUTED=palette.muted; ACCENT=palette.accent;
        getWindow().setBackgroundDrawable(shape(BG,0));
        getWindow().getDecorView().setSystemUiVisibility(palette.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout shell = column(); shell.setBackgroundColor(BG);
        shell.setForceDarkAllowed(false);
        shell.setOnApplyWindowInsetsListener((v, i) -> {
            v.setPadding(i.getSystemWindowInsetLeft(), i.getSystemWindowInsetTop(),
                i.getSystemWindowInsetRight(), i.getSystemWindowInsetBottom()); return i;
        });
        FrameLayout pages = new FrameLayout(this); shell.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        addressPage = new ScrollView(this); settingsPage = new ScrollView(this);
        addressPage.setClipToPadding(false); settingsPage.setClipToPadding(false);
        pages.addView(addressPage, new FrameLayout.LayoutParams(-1, -1));
        pages.addView(settingsPage, new FrameLayout.LayoutParams(-1, -1));
        detailsPage=new ScrollView(this);details=new WifiDetailsView(this,palette);detailsPage.addView(details);
        pages.addView(detailsPage,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout home = column(), settings = column();
        home.setPadding(dp(16), 0, dp(16), dp(24)); settings.setPadding(dp(16), 0, dp(16), dp(24));
        addressPage.addView(home); settingsPage.addView(settings);
        heading(home, "Wi-Fi MAC", "当前网络 · 地址管理");
        section(home, "当前连接");
        LinearLayout live = card(home);
        currentLabel = label("当前生效的 MAC · 正在读取", 15, ACCENT); live.addView(currentLabel);
        currentMac = label("正在读取…", 28, INK); currentMac.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        currentMac.setMaxLines(1); currentMac.setAutoSizeTextTypeUniformWithConfiguration(16, 28, 1, TypedValue.COMPLEX_UNIT_SP);
        currentMac.setTextIsSelectable(true); currentMac.setPadding(0, dp(9), 0, dp(8));
        live.addView(currentMac, new LinearLayout.LayoutParams(-1, dp(54)));
        currentNetwork = label("等待连接信息", 15, MUTED); live.addView(currentNetwork); gap(live, 16);
        copy = button("复制当前 MAC", true); enabled(copy, false);
        copy.setOnClickListener(v -> {
            if (actualMac.isEmpty()) return;
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("当前 MAC", actualMac));
            Toast.makeText(this, "已复制当前 MAC", Toast.LENGTH_SHORT).show();
        });
        live.addView(copy, new LinearLayout.LayoutParams(-2, -2));
        divider(live);
        live.addView(label("连接详情", 18, INK));
        connection = label("正在读取设备与链路地址", 14, MUTED); gap(live, 5); live.addView(connection);
        section(home, "修改地址");
        LinearLayout change = card(home);
        change.addView(label("待应用的新 MAC", 20, INK)); gap(change, 4);
        change.addView(label("输入或随机生成后，点击应用才会生效。", 14, MUTED)); gap(change, 14);
        mac = new EditText(this); mac.setSingleLine(true); mac.setTextSize(18); mac.setTextColor(INK);
        mac.setTypeface(Typeface.MONOSPACE); mac.setHint("02:12:34:56:78:9a"); mac.setHintTextColor(MUTED);
        mac.setText(state==null ? MacRules.random() : state.getString("draft",MacRules.random()));
        mac.setPadding(dp(14), dp(12), dp(14), dp(12)); mac.setBackground(shape(BG, 12));
        change.addView(mac, new LinearLayout.LayoutParams(-1, -2)); gap(change, 14);
        LinearLayout actions = new LinearLayout(this);
        Button random = button("随机生成", false); random.setOnClickListener(v -> mac.setText(MacRules.random()));
        actions.addView(random, new LinearLayout.LayoutParams(0, -2, 1));
        View space = new View(this); actions.addView(space, new LinearLayout.LayoutParams(dp(10), 1));
        apply = button("应用并重连", true); enabled(apply, false); apply.setOnClickListener(v -> request("apply"));
        actions.addView(apply, new LinearLayout.LayoutParams(0, -2, 1)); change.addView(actions);
        gap(change, 12); change.addView(label("仅作用于当前主 Wi-Fi，应用时会短暂断网。", 13, MUTED));
        section(home, "运行状态"); LinearLayout feedback = card(home);
        status = label("正在连接模块服务…", 14, MUTED); feedback.addView(status);

        heading(settings, "设置", "主题外观 · 模块状态 · 使用说明");
        appearance(settings);
        section(settings, "模块服务"); LinearLayout service = card(settings);
        service.addView(label("系统框架连接", 20, INK)); gap(service, 5);
        serviceState = label("正在检查…", 15, ACCENT); service.addView(serviceState); divider(service);
        Button refresh = button("刷新连接状态", false); refresh.setOnClickListener(v -> request("query"));
        service.addView(refresh, new LinearLayout.LayoutParams(-1, -2));
        section(settings, "恢复"); LinearLayout recovery = card(settings);
        recovery.addView(label("恢复系统地址策略", 20, INK)); gap(recovery, 5);
        recovery.addView(label("清除当前临时覆盖并重新连接。恢复后的地址由该网络原本的隐私设置决定。", 14, MUTED)); gap(recovery, 16);
        clear = button("恢复并重连", false); enabled(clear, false); clear.setOnClickListener(v -> request("clear"));
        recovery.addView(clear, new LinearLayout.LayoutParams(-1, -2));
        section(settings, "使用说明"); LinearLayout help = card(settings);
        help.addView(label("首次启用", 19, INK)); gap(help, 5);
        help.addView(label("在 LSPosed 启用模块，作用域选择系统框架，并重启一次。之后换地址无需重启手机。本次仅更新界面，无需重启。", 14, MUTED)); divider(help);
        help.addView(label("MLO 链路地址", 19, INK)); gap(help, 5);
        help.addView(label("顶部显示设备 MLD 地址。每条无线链路的地址由驱动派生，可能与设备地址不同。请在路由器在线列表核对设备地址。", 14, MUTED)); divider(help);
        help.addView(label("有效范围", 19, INK)); gap(help, 5);
        help.addView(label("仅修改当前主 Wi-Fi。同一网络重连时保留，切换其他网络或重启后清除。允许有效单播地址；请勿与同网设备重复。", 14, MUTED));
        section(settings, "关于"); LinearLayout about = card(settings);
        about.addView(label("Wi-Fi MAC  0.6.0", 19, INK)); gap(about, 5);
        about.addView(label("MLO 实验版 · LSPosed 模块\n诊断日志：WifiMacCurrent", 14, MUTED));

        View line = new View(this); line.setBackgroundColor(palette.divider); shell.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout nav = new LinearLayout(this); nav.setPadding(dp(12), dp(9), dp(12), dp(9));
        addressTab = label("地址", 16, ACCENT); settingsTab = label("设置", 16, MUTED);
        detailsTab=label("Wi-Fi 详情",16,MUTED);detailsTab.setGravity(Gravity.CENTER);detailsTab.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        addressTab.setGravity(Gravity.CENTER); settingsTab.setGravity(Gravity.CENTER);
        addressTab.setTypeface(Typeface.DEFAULT, Typeface.BOLD); settingsTab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        addressTab.setContentDescription("地址页面"); settingsTab.setContentDescription("设置页面");
        nav.addView(addressTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        nav.addView(detailsTab,new LinearLayout.LayoutParams(0,dp(48),1));
        nav.addView(settingsTab, new LinearLayout.LayoutParams(0, dp(48), 1)); shell.addView(nav);
        addressTab.setOnClickListener(v -> selectPage(0)); settingsTab.setOnClickListener(v -> selectPage(2)); detailsTab.setOnClickListener(v -> selectPage(1));
        setContentView(shell); selectPage(state==null ? 0 : state.getInt("page",0));
        if(state!=null) {
            addressPage.post(() -> addressPage.scrollTo(0,state.getInt("addressScroll")));
            settingsPage.post(() -> settingsPage.scrollTo(0,state.getInt("settingsScroll")));
            detailsPage.post(() -> detailsPage.scrollTo(0,state.getInt("detailsScroll")));
        }
    }

    private void appearance(LinearLayout settings) {
        section(settings,"外观"); LinearLayout card=card(settings);
        card.addView(label("显示模式",20,INK)); gap(card,6);
        card.addView(label("跟随系统可自动切换日间与夜间外观。",14,MUTED)); gap(card,14);
        LinearLayout modes=new LinearLayout(this);
        for(int i=0;i<ThemePalette.MODES.length;i++) {
            final int choice=i;
            Button b=button(ThemePalette.MODES[i],palette.mode==i); b.setTextSize(14); b.setPadding(dp(4),dp(10),dp(4),dp(10));
            b.setSelected(palette.mode==i); b.setContentDescription(ThemePalette.MODES[i]+(palette.mode==i?"，已选择":""));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1); if(i>0)p.leftMargin=dp(6);
            modes.addView(b,p); b.setOnClickListener(v -> changeAppearance(choice,palette.color));
        }
        card.addView(modes); divider(card);
        card.addView(label("主题配色",20,INK)); gap(card,6);
        card.addView(label("当前："+ThemePalette.NAMES[palette.color]+" · "+(palette.dark?"深色":"浅色"),14,MUTED)); gap(card,12);
        LinearLayout colors=new LinearLayout(this);
        for(int i=0;i<ThemePalette.NAMES.length;i++) {
            final int choice=i; boolean chosen=palette.color==i;
            LinearLayout item=column(); item.setGravity(Gravity.CENTER); item.setPadding(0,dp(10),0,dp(8));
            item.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.ripple),shape(chosen?palette.secondary:SURFACE,14),null));
            TextView dot=label(chosen?"✓":"",18,palette.onAccent); dot.setGravity(Gravity.CENTER); dot.setBackground(shape(palette.swatch(i),20));
            item.addView(dot,new LinearLayout.LayoutParams(dp(30),dp(30))); gap(item,7);
            TextView name=label(ThemePalette.NAMES[i],12,chosen?ACCENT:MUTED); name.setGravity(Gravity.CENTER); item.addView(name);
            item.setContentDescription(ThemePalette.NAMES[i]+"配色"+(chosen?"，已选择":"")); item.setSelected(chosen);
            item.setFocusable(true); item.setOnClickListener(v -> changeAppearance(palette.mode,choice));
            colors.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        card.addView(colors); gap(card,12);
        card.addView(label("切换立即生效，自动保存，无需重启手机。",13,MUTED));
    }
    private void changeAppearance(int mode,int color) {
        if(mode==palette.mode&&color==palette.color)return;
        ThemePalette.save(this,mode,color); recreate();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putInt("page",selectedPage); out.putString("draft",mac.getText().toString());
        out.putInt("addressScroll",addressPage.getScrollY()); out.putInt("settingsScroll",settingsPage.getScrollY());
        out.putInt("detailsScroll",detailsPage.getScrollY());
    }

    @Override protected void onResume() { super.onResume(); visible = true; ui.post(poll); }
    @Override protected void onPause() { visible = false; ui.removeCallbacks(poll); super.onPause(); }
    private void request(String op) {
        if (inFlight) return;
        Intent intent = new Intent(Protocol.ACTION).setPackage("android").putExtra("op", op).putExtra("key", key);
        if ("apply".equals(op)) {
            try { intent.putExtra("mac", MacRules.normalize(mac.getText().toString())); }
            catch (IllegalArgumentException e) { status.setText(e.getMessage()); return; }
        }
        inFlight = true; enabled(apply, false); enabled(clear, false);
        Runnable timeout = () -> {
            inFlight = false; currentLabel.setText("当前 MAC · 无法读取"); currentMac.setText("状态未知");
            currentNetwork.setText("控制服务未响应，当前地址尚未确认"); key = "";
            actualMac = ""; enabled(copy, false); serviceState.setText("未连接 · 请检查 LSPosed 作用域");
            status.setText("控制服务未响应。请检查 LSPosed 系统框架作用域，首次安装或升级 Hook 后重启一次。之后换地址无需重启。");
        };
        ui.postDelayed(timeout, 7000);
        try {
            sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
                @Override public void onReceive(Context ctx, Intent received) {
                    ui.removeCallbacks(timeout); inFlight = false;
                    if(isDestroyed())return;
                    Bundle b = getResultExtras(false);
                    if (b == null || !b.getBoolean("ready")) { timeout.run(); return; }
                    details.acceptModule(b);
                    key = b.getString("key", "");
                    String actual = b.getString("actual", "未连接");
                    String target = b.getString("target", "");
                    boolean busy = b.getBoolean("busy");
                    actualMac = key.isEmpty() || busy ? "" : actual;
                    enabled(copy, !actualMac.isEmpty());
                    serviceState.setText(busy ? "已连接 · 正在重连 Wi-Fi" : "已连接 · 模块服务正常");
                    currentLabel.setText(key.isEmpty() ? (busy ? "当前 MAC · 重连中" : "当前 MAC · 未连接")
                        : busy ? "当前 MAC · 重连中，系统读回" : "当前生效的 MAC · 系统读回");
                    currentMac.setText(key.isEmpty() ? (busy ? "正在重连…" : "未连接 Wi-Fi") : actual);
                    currentNetwork.setText("网络：" + b.getString("ssid", "无")
                        + (b.getBoolean("mlo") ? " · MLO 设备地址" : ""));
                    connection.setText((target.isEmpty() ? "使用系统地址策略" : "已提交目标：" + target)
                        + (b.getBoolean("mlo") ? "\n驱动派生的链路地址（与设备地址不同）：\n" + b.getString("links", "未知") : "") + "\n");
                    enabled(apply, !busy && !key.isEmpty()); enabled(clear, !busy && !target.isEmpty());
                    status.setText(b.getString("message", "服务已连接")
                        + (!target.isEmpty() && target.equalsIgnoreCase(actual) ? "\n系统读回与目标一致，请到路由器核对。" : "")
                        + (busy ? "\n正在处理连接，请稍候…" : ""));
                }
            }, ui, 0, null, null);
        } catch (Exception e) { ui.removeCallbacks(timeout); inFlight = false; status.setText("发送失败：" + e.getMessage()); }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);if(request==51)details.refresh();
    }
}
