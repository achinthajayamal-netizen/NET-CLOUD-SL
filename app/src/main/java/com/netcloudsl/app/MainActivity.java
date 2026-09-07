package com.netcloudsl.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends Activity {
    private LinearLayout root;
    private SharedPreferences prefs;
    private boolean connected = false;
    private String state = "NOT PROTECTED";
    private long connectedAt = 0L;
    private long rxStart = 0L, txStart = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String s = intent.getStringExtra("status");
            if (s == null) return;
            if (s.equals("CONNECTED")) {
                connected = true; state = "PROTECTED";
                if (connectedAt == 0) connectedAt = System.currentTimeMillis();
                rxStart = android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid());
                txStart = android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid());
            } else if (s.equals("DISCONNECTED")) {
                connected = false; state = "NOT PROTECTED"; connectedAt = 0;
            } else if (s.equals("CONNECTING") || s.equals("VERIFYING")) {
                connected = false; state = s;
            } else if (s.startsWith("ERROR")) {
                connected = false; state = "ERROR";
                Toast.makeText(MainActivity.this, s, Toast.LENGTH_LONG).show();
            }
            showHome();
        }
    };

    private final Runnable live = new Runnable() {
        @Override public void run() {
            if (connected) showHome();
            handler.postDelayed(this, 1500);
        }
    };

    int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    TextView tv(String text, float size) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(Color.WHITE); return t;
    }
    int blue() { return Color.rgb(20, 102, 235); }
    int bg() { return Color.rgb(7, 16, 31); }
    int card() { return Color.rgb(14, 30, 52); }
    int muted() { return Color.rgb(155, 174, 198); }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("netcloud_configs", MODE_PRIVATE);
        try { registerReceiver(receiver, new IntentFilter(NetCloudVpnService.ACTION_STATUS), Context.RECEIVER_NOT_EXPORTED); }
        catch (Throwable ignored) { registerReceiver(receiver, new IntentFilter(NetCloudVpnService.ACTION_STATUS)); }
        showHome(); handler.post(live);
    }

    @Override protected void onDestroy() { handler.removeCallbacks(live); try { unregisterReceiver(receiver); } catch (Throwable ignored) {} super.onDestroy(); }

    void base() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(10)); root.setBackgroundColor(bg());
        scroll.addView(root); setContentView(scroll);
    }

    TextView title(String text) { TextView t=tv(text,22); t.setTypeface(null,1); t.setPadding(0,0,0,dp(12)); return t; }

    void showHome() {
        base();
        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.netcloud_logo);
        brand.addView(icon, new LinearLayout.LayoutParams(dp(52),dp(52)));
        TextView n=tv("NET CLOUD SL",22); n.setTypeface(null,1); n.setTextColor(Color.rgb(245,190,45)); n.setPadding(dp(10),0,0,0);
        brand.addView(n); root.addView(brand);

        TextView sub=tv("Sri Lanka's Premier Cloud Network",13); sub.setTextColor(muted()); sub.setPadding(dp(62),dp(0),0,dp(10)); root.addView(sub);

        LinearLayout statusCard=panel(); statusCard.setOrientation(LinearLayout.VERTICAL); statusCard.setGravity(Gravity.CENTER); statusCard.setPadding(dp(16),dp(16),dp(16),dp(16));
        TextView st=tv(state,16); st.setTypeface(null,1); st.setTextColor(state.equals("PROTECTED")?Color.rgb(65,230,135):Color.rgb(255,190,70)); st.setGravity(Gravity.CENTER);
        statusCard.addView(st);
        TextView st2=tv(state.equals("PROTECTED")?"Your traffic is protected":"Tap connect to start the secure tunnel",12); st2.setTextColor(muted()); st2.setGravity(Gravity.CENTER); statusCard.addView(st2);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(82)); sp.bottomMargin=dp(14); root.addView(statusCard,sp);

        LinearLayout power=new LinearLayout(this); power.setOrientation(LinearLayout.VERTICAL); power.setGravity(Gravity.CENTER); power.setBackgroundResource(R.drawable.bg_button);
        TextView p=tv("⏻",50); p.setGravity(Gravity.CENTER); power.addView(p,new LinearLayout.LayoutParams(-1,dp(76)));
        TextView ct=tv(state.equals("PROTECTED")?"DISCONNECT":state.equals("CONNECTING")||state.equals("VERIFYING")?state:"CONNECT",17); ct.setTypeface(null,1); ct.setGravity(Gravity.CENTER); power.addView(ct);
        TextView hint=tv(state.equals("PROTECTED")?"Tap to stop":"Secure connection",12); hint.setTextColor(Color.WHITE); hint.setGravity(Gravity.CENTER); power.addView(hint);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(230),dp(220)); pp.gravity=Gravity.CENTER_HORIZONTAL; pp.bottomMargin=dp(16); root.addView(power,pp);
        power.setOnClickListener(v -> toggleVpn());

        LinearLayout serverCard=panel(); serverCard.setOrientation(LinearLayout.VERTICAL); serverCard.setPadding(dp(16),dp(14),dp(16),dp(14));
        String name=prefs.getString("active_name","No server selected"); String host=prefs.getString("active_host","Add a VLESS config"); String port=prefs.getString("active_port","443");
        TextView sn=tv("🌐  "+name,17); sn.setTypeface(null,1); serverCard.addView(sn);
        TextView ep=tv(host+":"+port+"  •  VLESS",13); ep.setTextColor(muted()); ep.setPadding(0,dp(6),0,0); serverCard.addView(ep);
        TextView info=tv(connected?"● Tunnel verified":"● Waiting for connection",12); info.setTextColor(connected?Color.rgb(65,230,135):muted()); info.setPadding(0,dp(5),0,0); serverCard.addView(info);
        LinearLayout.LayoutParams sc=new LinearLayout.LayoutParams(-1,dp(105)); sc.bottomMargin=dp(14); root.addView(serverCard,sc);

        LinearLayout stats=panel(); stats.setOrientation(LinearLayout.HORIZONTAL); stats.setPadding(dp(8),dp(10),dp(8),dp(10));
        long rx=android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid()); long tx=android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid());
        addMetric(stats,"↓",formatBytes(Math.max(0,rx-rxStart)),"DOWNLOAD"); addMetric(stats,"↑",formatBytes(Math.max(0,tx-txStart)),"UPLOAD"); addMetric(stats,"◷",formatDuration(),"SESSION");
        LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,dp(86)); stp.bottomMargin=dp(14); root.addView(stats,stp);

        addNav();
    }

    LinearLayout panel(){ LinearLayout l=new LinearLayout(this); l.setBackgroundResource(R.drawable.bg_card); return l; }
    void addMetric(LinearLayout parent,String icon,String value,String label){ LinearLayout x=new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL); x.setGravity(Gravity.CENTER); TextView a=tv(icon,20); a.setTextColor(Color.rgb(65,170,255)); x.addView(a); TextView v=tv(value,14); v.setTypeface(null,1); x.addView(v); TextView q=tv(label,9); q.setTextColor(muted()); x.addView(q); parent.addView(x,new LinearLayout.LayoutParams(0,-1,1)); }
    String formatBytes(long n){ if(n<1024) return n+" B"; if(n<1024*1024) return String.format("%.1f KB",n/1024.0); if(n<1024L*1024*1024) return String.format("%.1f MB",n/1024.0/1024); return String.format("%.2f GB",n/1024.0/1024/1024); }
    String formatDuration(){ if(!connected||connectedAt==0) return "00:00"; long sec=(System.currentTimeMillis()-connectedAt)/1000; return String.format("%02d:%02d",sec/60,sec%60); }

    void addNav(){ LinearLayout nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); String[] labels={"⌂\nHome","☁\nServers","▥\nStats","⚙\nSettings"}; for(int i=0;i<labels.length;i++){ TextView x=tv(labels[i],12); x.setGravity(Gravity.CENTER); final int tab=i; x.setOnClickListener(v->{if(tab==1)showConfigs();else if(tab==2)showStats();else if(tab==3)showSettings();}); nav.addView(x,new LinearLayout.LayoutParams(0,dp(68),1)); } root.addView(nav); }

    void toggleVpn(){
        if(state.equals("CONNECTING")||state.equals("VERIFYING")) return;
        if(connected){ Intent i=new Intent(this,NetCloudVpnService.class); i.setAction("STOP"); startService(i); return; }
        if(prefs.getString("active_uuid","").isEmpty()){ Toast.makeText(this,"Add a VLESS server first",Toast.LENGTH_SHORT).show(); showConfigs(); return; }
        Intent prep=VpnService.prepare(this); if(prep!=null){startActivityForResult(prep,100);return;} startVpnService();
    }
    void startVpnService(){ state="CONNECTING"; showHome(); startService(new Intent(this,NetCloudVpnService.class)); }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==100){ if(resultCode==RESULT_OK) startVpnService(); else Toast.makeText(this,"VPN permission is required",Toast.LENGTH_SHORT).show(); return; }
        if(requestCode==200 && resultCode==RESULT_OK && data!=null && data.getData()!=null){
            try { InputStream in=getContentResolver().openInputStream(data.getData()); BufferedReader br=new BufferedReader(new InputStreamReader(in)); StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line).append('\n'); br.close(); importVless(sb.toString().trim()); }
            catch(Exception e){ Toast.makeText(this,"Could not read config file: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
        }
    }

    void header(String text){ base(); TextView h=title("‹  "+text); h.setOnClickListener(v->showHome()); root.addView(h); }

    void showConfigs(){
        header("Servers & Configs");
        Button importUrl=new Button(this); importUrl.setText("＋  IMPORT VLESS URL"); root.addView(importUrl); importUrl.setOnClickListener(v->importVlessDialog());
        Button importFile=new Button(this); importFile.setText("▣  IMPORT CONFIG FILE"); root.addView(importFile); importFile.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("text/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,200);});
        TextView hint=tv("\nSupported: VLESS • TCP • TLS • Reality • WS • gRPC\nSelect a server below to make it active.",13); hint.setTextColor(muted()); root.addView(hint);
        int count=prefs.getInt("count",0); if(count==0){TextView e=tv("\nNo servers yet. Import a VLESS link to begin.",16);e.setTextColor(muted());root.addView(e);}
        for(int i=0;i<count;i++){ final int idx=i; LinearLayout c=panel();c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(12),dp(14),dp(12)); String id=prefs.getString("id_"+i,"");String name=prefs.getString("name_"+i,"VLESS Server");String host=prefs.getString("host_"+i,"");String port=prefs.getString("port_"+i,"443"); TextView a=tv((id.equals(prefs.getString("active_uuid",""))?"● ACTIVE   ":"")+name,16);a.setTypeface(null,1);c.addView(a);TextView d=tv(host+":"+port+" • "+prefs.getString("security_"+i,"none").toUpperCase(),12);d.setTextColor(muted());c.addView(d);Button use=new Button(this);use.setText("USE SERVER");use.setOnClickListener(v->{prefs.edit().putString("active_uuid",id).putString("active_name",name).putString("active_host",host).putString("active_port",port).apply();Toast.makeText(this,"Active: "+name,Toast.LENGTH_SHORT).show();showHome();});Button del=new Button(this);del.setText("DELETE");del.setOnClickListener(v->deleteConfig(idx));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.END);row.addView(use);row.addView(del);c.addView(row);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(150));cp.topMargin=dp(10);root.addView(c,cp); }
    }

    void importVlessDialog(){ final EditText input=new EditText(this);input.setHint("vless://UUID@server:443?...#Name");input.setMinLines(4);new AlertDialog.Builder(this).setTitle("Import VLESS").setView(input).setPositiveButton("IMPORT",(d,w)->importVless(input.getText().toString().trim())).setNegativeButton("CANCEL",null).show(); }

    void importVless(String raw){
        try{
            if(!raw.toLowerCase().startsWith("vless://"))throw new IllegalArgumentException("Not a VLESS URI");
            Uri u=Uri.parse(raw);String uuid=u.getUserInfo();String host=u.getHost();int port=u.getPort()>0?u.getPort():443;if(uuid==null||uuid.isEmpty()||host==null||host.isEmpty())throw new IllegalArgumentException("Missing UUID or server host");
            String name=u.getFragment();name=(name==null||name.isEmpty())?"VLESS "+host:URLDecoder.decode(name,StandardCharsets.UTF_8.name());String q=u.getQuery()==null?"":u.getQuery();
            int count=prefs.getInt("count",0);String id=UUID.randomUUID().toString();SharedPreferences.Editor e=prefs.edit();e.putInt("count",count+1).putString("id_"+count,id).putString("name_"+count,name).putString("uuid_"+count,uuid).putString("host_"+count,host).putString("port_"+count,String.valueOf(port));
            String[] keys={"type","security","sni","path","fp","pbk","sid","flow","alpn","allowInsecure"};String[] def={"tcp","none",host,"","","","","","","0"};for(int i=0;i<keys.length;i++)e.putString(keys[i]+"_"+count,param(q,keys[i],def[i]));e.putString("active_uuid",id).putString("active_name",name).putString("active_host",host).putString("active_port",String.valueOf(port)).apply();Toast.makeText(this,"VLESS imported",Toast.LENGTH_SHORT).show();showConfigs();
        }catch(Exception ex){new AlertDialog.Builder(this).setTitle("Import failed").setMessage(ex.getMessage()).setPositiveButton("OK",null).show();}
    }
    String param(String q,String key,String fallback){for(String p:q.split("&")){int x=p.indexOf('=');if(x>0&&p.substring(0,x).equals(key))return Uri.decode(p.substring(x+1));}return fallback;}

    @Override protected void onResume(){super.onResume();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);}

    void deleteConfig(int index){int count=prefs.getInt("count",0);if(index<0||index>=count)return;SharedPreferences.Editor e=prefs.edit();String[] keys={"id","name","uuid","host","port","type","security","sni","path","fp","pbk","sid","flow","alpn","allowInsecure"};for(int i=index;i<count-1;i++)for(String k:keys)e.putString(k+"_"+i,prefs.getString(k+"_"+(i+1),""));for(String k:keys)e.remove(k+"_"+(count-1));e.putInt("count",count-1);e.apply();showConfigs();}

    void showStats(){header("Statistics");long rx=android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid());long tx=android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid());TextView s=tv("\nLIVE SESSION\n\n↓  Download   "+formatBytes(Math.max(0,rx-rxStart))+"\n\n↑  Upload       "+formatBytes(Math.max(0,tx-txStart))+"\n\n◷  Duration     "+formatDuration()+"\n\n●  Status       "+state+"\n\nThese counters are local app traffic counters.",17);s.setTextColor(Color.WHITE);root.addView(s);}

    void showSettings(){
        header("Settings");
        TextView about=tv("\nNET CLOUD SL V2\n\nSecure VLESS/Xray client for Android\n\nCore: XTLS/libXray\nVPN: Android VpnService\n\n",17);about.setTextColor(Color.WHITE);root.addView(about);
        Switch auto=new Switch(this);auto.setText("Auto reconnect");auto.setTextColor(Color.WHITE);auto.setChecked(prefs.getBoolean("auto_reconnect",false));auto.setOnCheckedChangeListener((b,c)->prefs.edit().putBoolean("auto_reconnect",c).apply());root.addView(auto);
        Switch dns=new Switch(this);dns.setText("Protected DNS");dns.setTextColor(Color.WHITE);dns.setChecked(true);dns.setEnabled(false);root.addView(dns);
        Button appInfo=new Button(this);appInfo.setText("APP INFORMATION");appInfo.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));root.addView(appInfo);
    }

    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("connected",connected);out.putString("state",state);out.putLong("connectedAt",connectedAt);}
    @Override protected void onRestoreInstanceState(Bundle in){super.onRestoreInstanceState(in);if(in!=null){connected=in.getBoolean("connected",false);state=in.getString("state","NOT PROTECTED");connectedAt=in.getLong("connectedAt",0);}}
}
