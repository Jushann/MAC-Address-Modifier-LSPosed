package dev.local.wifimac;
import android.app.Activity;
import android.net.*;
import android.os.*;
import android.widget.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Explicit, foreground-only diagnostics; no changes to network routing. */
final class IpDiagnosticsView extends LinearLayout {
 final Activity a; final ThemePalette p; final Map<String,TextView> fields=new LinkedHashMap<>();
 final Map<String,String> values=new LinkedHashMap<>(); final ExecutorService pool=Executors.newFixedThreadPool(4);
 boolean hidden,busy; int generation; Button start; String httpIp=""; Network snapshot;
 interface Check {String run() throws Exception;}
 IpDiagnosticsView(Activity a,ThemePalette p){super(a);this.a=a;this.p=p;setOrientation(VERTICAL);setPadding(dp(16),dp(30),dp(16),dp(24));
 addView(text("IP 检测",34,p.ink));addView(text("公网出口 · 网络诊断 · 免费版",15,p.muted));
 group("检测选项");LinearLayout controls=card();
 controls.addView(text("按需检查当前网络",19,p.ink));
 TextView note=text("点击后联网，沿用系统默认路由（含 VPN）。检测服务会看到出口 IP，UDP 出口可能不同。结果不代表 Claude 账号可用性。",14,p.muted);note.setPadding(0,dp(6),0,dp(12));controls.addView(note);
 Switch mask=new Switch(a);mask.setText("隐藏 IP / 检测结果");mask.setTextSize(16);mask.setTextColor(p.ink);mask.setPadding(0,dp(8),0,dp(8));mask.setMinimumHeight(dp(48));
 android.content.res.ColorStateList tint=new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{p.accent,p.disabled});mask.setThumbTintList(tint);mask.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{p.secondary,p.divider}));
 mask.setOnCheckedChangeListener((button,v)->{hidden=v;render();});controls.addView(mask);
 start=new Button(a);start.setText("开始检测 / 刷新");start.setTextSize(15);start.setAllCaps(false);start.setTypeface(Typeface.DEFAULT,Typeface.BOLD);start.setMinHeight(dp(48));start.setMinimumHeight(dp(48));start.setPadding(dp(18),dp(10),dp(18),dp(10));start.setStateListAnimator(null);
 start.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{p.disabled,p.onAccent}));start.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(p.ripple),shape(p.accent,18),null));
 LayoutParams action=new LayoutParams(-1,-2);action.topMargin=dp(12);controls.addView(start,action);start.setOnClickListener(v->runChecks());
 for(String key:new String[]{"检测状态","国内服务出口 · IPIP","Cloudflare 出口","Claude 站点出口","IPinfo 属性","信誉与安全","网站响应 / 延迟","Claude 服务状态","DNS 配置与泄露","UDP 出口 · STUN","时区与客户端"}){
 group(key);LinearLayout panel=card();TextView value=text("未检测",16,key.equals("检测状态")?p.accent:p.ink);panel.addView(value);fields.put(key,value);values.put(key,"未检测");}
 put("信誉与安全","信誉评分、住宅/机房、VPN、代理、Tor、机器人、滥用历史：免费无 Token 模式没有可靠数据，均为未知。IPinfo 的 abuse 联系人并不是滥用记录。支持地区不作自动判定。");
 put("时区与客户端","系统时区："+TimeZone.getDefault().getID()+"\nAndroid "+Build.VERSION.RELEASE+"；本检测为原生客户端，不提供浏览器指纹评分。");
 }
 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 TextView text(String s,int size,int color){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
 GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 void group(String s){TextView t=text(s,16,p.accent);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(dp(10),dp(22),0,dp(10));addView(t);}
 LinearLayout card(){LinearLayout c=new LinearLayout(a);c.setOrientation(VERTICAL);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(shape(p.surface,22));addView(c,new LayoutParams(-1,-2));return c;}
 void put(String k,String v){values.put(k,v);render();}
 void render(){for(String k:fields.keySet())fields.get(k).setText(hidden?"已隐藏":values.get(k));}
 ConnectivityManager cm(){return a.getSystemService(ConnectivityManager.class);}
 void task(String key,int gen,Check c,CountDownLatch done){pool.execute(()->{String result;try{result=c.run();}catch(Exception e){result="未完成："+e.getClass().getSimpleName()+"（网络不可达、超时或服务限制；不表示安全）";}final String r=result;a.runOnUiThread(()->{if(gen==generation&&!a.isDestroyed())put(key,r);});done.countDown();});}
 void runChecks(){if(busy)return;busy=true;start.setEnabled(false);httpIp="";snapshot=cm().getActiveNetwork();int gen=++generation;CountDownLatch done=new CountDownLatch(7);for(String k:new String[]{"国内服务出口 · IPIP","Cloudflare 出口","Claude 站点出口","IPinfo 属性","网站响应 / 延迟","Claude 服务状态","UDP 出口 · STUN"})put(k,"检测中…");put("检测状态","检测中；切换网络后请重新检测。");
 task("国内服务出口 · IPIP",gen,()->get("https://myip.ipip.net").trim()+"\n仅代表访问 IPIP 时的出口，不表示地址一定在中国。",done);
 task("Cloudflare 出口",gen,()->{String ip=trace("https://www.cloudflare.com/cdn-cgi/trace");httpIp=ip;return ip+"\n来源：www.cloudflare.com";},done);
 task("Claude 站点出口",gen,()->trace("https://claude.ai/cdn-cgi/trace")+"\n来源：claude.ai 边缘诊断；不代表对话请求或账号可用。",done);
 task("IPinfo 属性",gen,()->{JSONObject j=new JSONObject(get("https://ipinfo.io/json"));if(!j.has("ip"))throw new IOException();return "IP："+j.optString("ip","未报告")+"\n国家："+j.optString("country","未报告")+"\n地区 / 城市："+j.optString("region","未报告")+" / "+j.optString("city","未报告")+"\nASN / 运营商："+j.optString("org","未报告")+"\nIP 时区："+j.optString("timezone","未报告")+"\n来源：IPinfo 无 Token 接口（可能限流）；仅描述该请求出口。";},done);
 task("网站响应 / 延迟",gen,()->probe("https://claude.ai")+"\n"+probe("https://www.anthropic.com")+"\n响应耗时包含 DNS/TLS；403/429 不等于网络断开，2xx 不等于账号可用。",done);
 task("Claude 服务状态",gen,()->new JSONObject(get("https://status.anthropic.com/api/v2/status.json")).getJSONObject("status").getString("description")+"\n来源：Anthropic 状态页（全局状态）",done);
 task("UDP 出口 · STUN",gen,()->stun()+"\n来源：stun.l.google.com:19302；原生 IPv4 UDP 探测，不是浏览器 WebRTC 测试。出口差异可能由分流造成，不能单凭差异认定泄露。",done);
 LinkProperties lp=cm().getLinkProperties(snapshot);put("DNS 配置与泄露",lp==null?"未检测到默认网络":"系统 DNS："+lp.getDnsServers()+"\n私有 DNS："+(lp.isPrivateDnsActive()?"已启用":"未启用")+"\nDNS 递归出口 / 服务商 / 泄露结论：未知。需要权威 DNS 回查服务；系统 DNS 地址不能代替递归出口。");
 new Thread(()->{try{done.await(60,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}a.runOnUiThread(()->{if(gen!=generation||a.isDestroyed())return;busy=false;start.setEnabled(true);put("检测状态",Objects.equals(snapshot,cm().getActiveNetwork())?"完成 · "+new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date())+"\n各卡片独立报告，失败不等于安全。":"检测期间默认网络已变化，结果可能混合，请重新检测。");});}).start();
 }
 String get(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setRequestProperty("User-Agent","WifiMacCurrent/0.7.0");try{int code=c.getResponseCode();if(code!=200)throw new IOException("HTTP "+code);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>131072)throw new IOException();out.write(b,0,n);}return out.toString("UTF-8");}}finally{c.disconnect();}}
 String trace(String url)throws Exception{for(String line:get(url).split("\n"))if(line.startsWith("ip=")){String ip=line.substring(3).trim();if(ip.matches("[0-9a-fA-F:.]+"))return ip;}throw new IOException();}
 String probe(String url){long t=SystemClock.elapsedRealtime();HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);return url+"：HTTP "+c.getResponseCode()+" · "+(SystemClock.elapsedRealtime()-t)+" ms";}catch(Exception e){return url+"：连接未完成";}finally{if(c!=null)c.disconnect();}}
 String stun()throws Exception{byte[] req=new byte[20];req[1]=1;req[4]=0x21;req[5]=0x12;req[6]=(byte)0xa4;req[7]=0x42;byte[] id=new byte[12];new java.security.SecureRandom().nextBytes(id);System.arraycopy(id,0,req,8,12);InetAddress host=null;for(InetAddress h:InetAddress.getAllByName("stun.l.google.com"))if(h instanceof Inet4Address){host=h;break;}if(host==null)throw new IOException();try(DatagramSocket s=new DatagramSocket()){s.connect(host,19302);s.setSoTimeout(6000);s.send(new DatagramPacket(req,req.length));byte[] b=new byte[2048];DatagramPacket p=new DatagramPacket(b,b.length);s.receive(p);int len=p.getLength();if(len<20||b[0]!=1||b[1]!=1)throw new IOException();for(int i=4;i<20;i++)if(b[i]!=req[i])throw new IOException();int end=20+((b[2]&255)<<8)+(b[3]&255);if(end>len)throw new IOException();for(int o=20;o+4<=end;){int type=((b[o]&255)<<8)|(b[o+1]&255),n=((b[o+2]&255)<<8)|(b[o+3]&255);if(o+4+n>end)throw new IOException();if((type==0x20||type==1)&&n>=8&&b[o+5]==1){byte[] ip=new byte[4];for(int j=0;j<4;j++)ip[j]=(byte)(b[o+8+j]^(type==0x20?req[4+j]:0));return InetAddress.getByAddress(ip).getHostAddress();}o+=4+((n+3)&~3);}throw new IOException();}}
 void dispose(){generation++;pool.shutdownNow();}
}
