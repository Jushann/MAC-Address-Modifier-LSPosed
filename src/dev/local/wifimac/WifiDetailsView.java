package dev.local.wifimac;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import android.widget.*;
import java.net.*;
import java.io.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

/** App-side diagnostics only. No change to the system-server MAC hooks. */
public final class WifiDetailsView extends LinearLayout {
    private final Activity activity;
    private final ConnectivityManager cm;
    private final WifiManager wm;
    private final Map<String, TextView> rows = new LinkedHashMap<>();
    private TextView summary, access, publicResult, route;
    private Button publicButton, directButton;
    private Bundle module = new Bundle();
    private Network wifi;
    private String fingerprint = "";
    private boolean querying;
    private final Map<Network,WifiInfo> observed=new HashMap<>();
    private ConnectivityManager.NetworkCallback callback;
    private boolean listenerFine;
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();listen();}
    private void listen(){
        if(callback!=null){try{cm.unregisterNetworkCallback(callback);}catch(Exception ignored){}}
        observed.clear();listenerFine=activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        callback=new ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO){
            @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities c){if(c.getTransportInfo() instanceof WifiInfo)observed.put(n,(WifiInfo)c.getTransportInfo());}
            @Override public void onLost(Network n){observed.remove(n);}
        };
        try{cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),callback,new Handler(Looper.getMainLooper()));}catch(Exception e){callback=null;}
    }
    @Override protected void onDetachedFromWindow(){if(callback!=null){cm.unregisterNetworkCallback(callback);callback=null;}observed.clear();super.onDetachedFromWindow();}
    private int queryTicket;
    private final int ink, muted, accent;
    private final ThemePalette palette;
    public WifiDetailsView(Activity a, ThemePalette colors) {
        super(a); activity=a; setOrientation(VERTICAL); setPadding(dp(16),dp(30),dp(16),dp(24));
        palette=colors; ink=colors.ink; muted=colors.muted; accent=colors.accent;
        cm=a.getSystemService(ConnectivityManager.class); wm=a.getApplicationContext().getSystemService(WifiManager.class);
        addView(text("Wi-Fi 详情",34,ink)); addView(text("无线连接 · 局域网 · 公网出口",15,muted));
        group("当前网络"); LinearLayout head=card(); summary=text("正在读取…",23,ink); head.addView(summary);
        access=text("",14,muted); head.addView(access);
        Button permission=button("授权读取 Wi-Fi 信息",false); head.addView(permission);
        permission.setOnClickListener(v -> activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.NEARBY_WIFI_DEVICES},51));
        Button refresh=button("刷新详情",false); head.addView(refresh); refresh.setOnClickListener(v -> refresh());
        group("公网 IP"); LinearLayout pub=card();
        pub.addView(text("查询服务看到的出口地址",19,ink));
        route=text("",14,muted); pub.addView(route);
        pub.addView(text("按需通过 ipify、ipw.cn 查询 IPv4 / IPv6，IPv4 另有 IPIP 备用服务。不上传 Wi-Fi 名称或 MAC。VPN、代理及运营商 NAT 会影响出口。",14,muted));
        publicButton=button("查询当前出口 IP",true); pub.addView(publicButton); publicButton.setOnClickListener(v -> queryPublic(false));
        directButton=button("查询 Wi-Fi 直连出口（绕过 VPN）",false); pub.addView(directButton); directButton.setOnClickListener(v -> queryPublic(true));
        publicResult=text("尚未查询",16,accent); publicResult.setTextIsSelectable(true); pub.addView(publicResult);
        group("无线参数"); LinearLayout radio=card();
        for(String n:new String[]{"SSID","Wi-Fi 标准","频段 / 信道","信号强度","收发协商速率","设备支持的最高速率","认证方式","BSSID（接入点）","设备 MAC / MLD","MLO 链路"}) row(radio,n);
        radio.addView(text("速率是无线链路协商值，不是实际下载速度。MLO 的频率和收发速率代表系统上报的关联链路。",13,muted));
        group("局域网地址"); LinearLayout lan=card();
        for(String n:new String[]{"网络接口","IPv4 / 前缀","IPv4 子网掩码","IPv6 / 前缀","默认网关","DNS 服务器","私有 DNS","MTU","HTTP 代理"}) row(lan,n);
        group("连接状态"); LinearLayout state=card();
        for(String n:new String[]{"当前默认路由","Wi-Fi 互联网验证","计费网络","连接状态"}) row(state,n);
        state.addView(text("未授权定位或系统定位关闭时，SSID / BSSID 可能被系统隐藏。部分 ROM 或其他模块也可能过滤应用可读取的信息。",13,muted));
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){TextView t=new TextView(activity);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private void group(String s){TextView t=text(s,16,accent);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(dp(10),dp(22),0,dp(10));addView(t);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(20));return d;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(activity);c.setOrientation(VERTICAL);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(bg(palette.surface));addView(c,new LayoutParams(-1,-2));return c;}
    private Button button(String title,boolean filled){Button b=new Button(activity);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{palette.disabled,filled?palette.onAccent:accent}));b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(palette.ripple),bg(filled?accent:palette.secondary),null));b.setMinHeight(dp(48));b.setPadding(dp(12),dp(8),dp(12),dp(8));LayoutParams p=new LayoutParams(-1,-2);p.topMargin=dp(12);p.bottomMargin=dp(10);b.setLayoutParams(p);return b;}
    private void row(LinearLayout parent,String name){TextView title=text(name,14,muted);title.setPadding(0,dp(9),0,dp(3));parent.addView(title);TextView value=text("未读取",17,ink);value.setTextIsSelectable(true);parent.addView(value);rows.put(name,value);}
    private void put(String name,String value){rows.get(name).setText(value==null||value.isEmpty()?"未报告":value);}
    public void acceptModule(Bundle b){module=new Bundle(b);}
    private static String speed(int n){return n>0?n+" Mbps":"未报告";}
    public static String standard(int n,int frequency){switch(n){case 1:return "802.11a/b/g（传统 Wi-Fi）";case 4:return "802.11n · Wi-Fi 4";case 5:return "802.11ac · Wi-Fi 5";case 6:return "802.11ax · "+(frequency>=5925?"Wi-Fi 6E":"Wi-Fi 6");case 7:return "802.11ad · WiGig";case 8:return "802.11be · Wi-Fi 7";default:return "系统未报告";}}
    private static String security(int n){String[] names={"开放网络","WEP","WPA/WPA2-Personal","WPA/WPA2-Enterprise","WPA3-SAE","WPA3-Enterprise 192-bit","OWE","WAPI-PSK","WAPI-CERT","WPA3-Enterprise","OSEN","Passpoint R1/R2","Passpoint R3","DPP"};return n>=0&&n<names.length?names[n]:"系统未报告（"+n+"）";}
    private String path(Network n){NetworkCapabilities c=n==null?null:cm.getNetworkCapabilities(n);if(c==null)return "无可用默认网络";if(c.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return "VPN（查询结果可能为 VPN 出口）";if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return "Wi-Fi";if(c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))return "移动数据（不是 Wi-Fi 出口）";return "其他网络";}
    public void refresh(){
        try {
            WifiInfo w=wm.getConnectionInfo();
            boolean fine=activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            if(isAttachedToWindow()&&listenerFine!=fine)listen();
            boolean location=activity.getSystemService(LocationManager.class).isLocationEnabled();
            access.setText(fine&&location?"本地参数自动刷新；公网 IP 仅在点击时查询。":"读取完整 SSID / BSSID 需要精确位置权限及系统定位开启。不会收集位置轨迹。");
            Network active=cm.getActiveNetwork(); wifi=null; List<Network> candidates=new ArrayList<>();
            for(Network n:cm.getAllNetworks()){NetworkCapabilities nc=cm.getNetworkCapabilities(n);if(nc!=null&&nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN))candidates.add(n);}
            if(candidates.contains(active))wifi=active;
            if(wifi==null&&w!=null&&w.getIpAddress()!=0){int ip=w.getIpAddress();String expected=(ip&255)+"."+((ip>>8)&255)+"."+((ip>>16)&255)+"."+((ip>>24)&255);for(Network n:candidates){LinkProperties lp=cm.getLinkProperties(n);if(lp!=null)for(LinkAddress la:lp.getLinkAddresses())if(expected.equals(la.getAddress().getHostAddress()))wifi=n;}}
            if(wifi==null&&candidates.size()==1)wifi=candidates.get(0);
            LinkProperties lp=wifi==null?null:cm.getLinkProperties(wifi);
            // Prefer the Wi-Fi network's own transport information. The legacy manager API
            // may be redacted or modified by ROM/privacy modules independently of connectivity.
            NetworkCapabilities wifiCaps=wifi==null?null:cm.getNetworkCapabilities(wifi);
            if(wifiCaps!=null&&wifiCaps.getTransportInfo() instanceof WifiInfo)w=(WifiInfo)wifiCaps.getTransportInfo();
            if(observed.containsKey(wifi))w=observed.get(wifi);
            String mark=String.valueOf(active)+"|"+wifi+"|"+(lp==null?"":lp.getLinkAddresses().toString());
            if(!mark.equals(fingerprint)){fingerprint=mark;queryTicket++;querying=false;publicResult.setText("网络已变化，请重新查询公网 IP");}
            route.setText("当前默认出口："+path(active)+"\nWi-Fi 直连查询仅在点击下方独立按钮后执行。");
            publicButton.setEnabled(!querying&&active!=null);directButton.setEnabled(!querying&&wifi!=null);
            for(TextView value:rows.values())value.setText("未连接 / 未报告");
            put("当前默认路由",path(active));
            if(lp==null){summary.setText(candidates.size()>1?"存在多个 Wi-Fi，无法确定主网络":"未检测到可用 Wi-Fi");return;}
            int frequency=w==null?0:w.getFrequency();
            summary.setText(w==null?"Wi-Fi 已连接":standard(w.getWifiStandard(),frequency));
            if(w!=null){
                put("SSID",module.getString("ssid",w.getSSID()));put("Wi-Fi 标准",standard(w.getWifiStandard(),frequency));
                String band=frequency>=57000?"60 GHz":frequency>=5925?"6 GHz":frequency>=4900?"5 GHz":frequency>=2400?"2.4 GHz":"未知频段";
                put("频段 / 信道",band+" · "+frequency+" MHz · 信道 "+ScanResult.convertFrequencyMhzToChannelIfSupported(frequency));
                put("信号强度",w.getRssi()+" dBm");put("收发协商速率","发送 "+speed(w.getTxLinkSpeedMbps())+" / 接收 "+speed(w.getRxLinkSpeedMbps()));
                put("设备支持的最高速率","发送 "+speed(w.getMaxSupportedTxLinkSpeedMbps())+" / 接收 "+speed(w.getMaxSupportedRxLinkSpeedMbps()));
                put("认证方式",security(w.getCurrentSecurityType()));put("BSSID（接入点）","02:00:00:00:00:00".equals(w.getBSSID())?"被系统隐藏（请检查权限）":w.getBSSID());put("连接状态",String.valueOf(w.getSupplicantState()));
            }
            put("设备 MAC / MLD",module.getString("actual","模块未返回地址"));put("MLO 链路",module.getString("links","无链路信息"));
            put("网络接口",lp.getInterfaceName());List<String> v4=new ArrayList<>(),v6=new ArrayList<>(),masks=new ArrayList<>();
            for(LinkAddress la:lp.getLinkAddresses()){InetAddress a=la.getAddress();if(a instanceof Inet4Address){v4.add(a.getHostAddress()+" / "+la.getPrefixLength());int mask=la.getPrefixLength()==0?0:(int)(0xffffffffL<<(32-la.getPrefixLength()));masks.add(((mask>>>24)&255)+"."+((mask>>>16)&255)+"."+((mask>>>8)&255)+"."+(mask&255));}else v6.add(a.getHostAddress()+" / "+la.getPrefixLength()+(a.isLinkLocalAddress()?"（链路本地）":a.isSiteLocalAddress()?"（本地地址）":""));}
            put("IPv4 / 前缀",String.join("\n",v4));put("IPv4 子网掩码",String.join("\n",masks));put("IPv6 / 前缀",String.join("\n",v6));
            List<String> gateways=new ArrayList<>();for(RouteInfo r:lp.getRoutes())if(r.isDefaultRoute()&&r.hasGateway())gateways.add(r.getGateway().getHostAddress());put("默认网关",String.join("\n",gateways));
            List<String> dns=new ArrayList<>();for(InetAddress d:lp.getDnsServers())dns.add(d.getHostAddress());put("DNS 服务器",String.join("\n",dns));
            put("私有 DNS",lp.isPrivateDnsActive()?(lp.getPrivateDnsServerName()==null?"已启用（自动模式）":lp.getPrivateDnsServerName()):"系统报告未启用");
            put("MTU",lp.getMtu()>0?Integer.toString(lp.getMtu()):"系统默认");ProxyInfo proxy=lp.getHttpProxy();put("HTTP 代理",proxy==null?"未配置":proxy.getPacFileUrl()!=null&&!Uri.EMPTY.equals(proxy.getPacFileUrl())?"PAC："+proxy.getPacFileUrl():proxy.getHost()+":"+proxy.getPort());
            NetworkCapabilities nc=cm.getNetworkCapabilities(wifi);
            if(nc!=null){put("Wi-Fi 互联网验证",nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?"系统已验证互联网连接":nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)?"需要网页认证":"尚未通过系统验证");put("计费网络",nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)?"不按流量计费":"按流量计费");}
        }catch(Exception e){summary.setText("读取失败："+e.getClass().getSimpleName());}
    }
    private void queryPublic(boolean direct){
        refresh();if(querying)return;final Network selected=direct?wifi:cm.getActiveNetwork();if(selected==null)return;
        final String stamp=fingerprint;final int ticket=++queryTicket;querying=true;publicButton.setEnabled(false);directButton.setEnabled(false);
        final String mode=direct?"Wi-Fi 直连出口":"当前默认出口（"+path(selected)+"）";
        publicResult.setText(mode+"\n正在分别查询 IPv4 / IPv6…");
        new Thread(() -> {
            // Default mode follows normal per-app VPN/proxy routing. Only the explicit
            // direct-Wi-Fi action binds sockets to a particular Network.
            String v4=lookup(direct?selected:null,false);
            String v6=lookup(direct?selected:null,true);
            activity.runOnUiThread(() -> {
                if(activity.isDestroyed())return;refresh();if(ticket!=queryTicket||!stamp.equals(fingerprint))return;
                querying=false;publicButton.setEnabled(cm.getActiveNetwork()!=null);directButton.setEnabled(wifi!=null);
                publicResult.setText(mode+"\nIPv4："+v4+"\nIPv6："+v6+"\n查询时间："+new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date())+"\n通过 HTTPS 查询，成功结果附带来源。");
            });
        },"wifi-public-ip").start();
    }
    private static String lookup(Network n,boolean ipv6){
        String result=fetch(n,ipv6?"https://api6.ipify.org?format=json":"https://api.ipify.org?format=json",ipv6);
        if(!result.startsWith("未取得"))return result+"（ipify）";
        result=fetch(n,ipv6?"https://6.ipw.cn":"https://4.ipw.cn",ipv6);
        if(!ipv6&&result.startsWith("未取得")){
            result=fetch(n,"https://myip.ipip.net",false);
            return result.startsWith("未取得")?result:result+"（IPIP）";
        }
        return result.startsWith("未取得")?result:result+"（ipw.cn）";
    }
    private static String fetch(Network n,String url,boolean ipv6){
        HttpsURLConnection c=null;
        try{
            c=(HttpsURLConnection)(n==null?new URL(url).openConnection():n.openConnection(new URL(url)));c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setUseCaches(false);c.setInstanceFollowRedirects(false);
            if(c.getResponseCode()!=200)return "未取得（服务响应 HTTP "+c.getResponseCode()+"）";
            ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] bytes=new byte[512];int size;while((size=in.read(bytes))!=-1){if(out.size()+size>2048)throw new IOException("Response too large");out.write(bytes,0,size);}}
            String body=out.toString("UTF-8").trim();String ip=body.startsWith("{")?new org.json.JSONObject(body).getString("ip"):body;
            if(url.equals("https://myip.ipip.net")){
                java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?<![0-9])([0-9]{1,3}(?:\\.[0-9]{1,3}){3})(?![0-9])").matcher(body);
                if(!m.find())throw new IOException("Missing IP");ip=m.group(1);
            }
            if(!ip.matches(ipv6?"[0-9a-fA-F:]+":"[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new IOException("Not an IP literal");
            InetAddress a=InetAddress.getByName(ip);if(ipv6!=(a instanceof Inet6Address))throw new IOException("Wrong address family");return ip;
        }catch(Exception e){android.util.Log.w("WifiDetails","Public IP query failed: "+url,e);return "未取得（"+(e instanceof UnknownHostException?"DNS 解析失败或不支持该 IP 协议":e instanceof java.net.SocketTimeoutException?"连接超时":e instanceof java.net.ConnectException?"无法连接查询服务":"查询失败")+"）";}
        finally{if(c!=null)c.disconnect();}
    }
}
