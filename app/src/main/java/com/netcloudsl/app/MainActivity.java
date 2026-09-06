package com.netcloudsl.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {
    private LinearLayout root;
    private TextView status, server, ping, connectText;
    private boolean connected = false;
    private SharedPreferences prefs;

    int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView tv(String s, float size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.rgb(245,245,247));
        t.setTextSize(size);
        return t;
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("netcloud_configs", MODE_PRIVATE);
        showHome();
    }

    void base() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(10));
        root.setBackgroundColor(Color.rgb(9,9,11));
        setContentView(root);
    }

    void showHome() {
        base();

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_netcloud);
        brand.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView name = tv("  NET CLOUD SL", 21);
        name.setTypeface(null, 1);
        brand.addView(name);
        root.addView(brand);

        status = tv(connected ? "●  PROTECTED" : "●  NOT PROTECTED", 14);
        status.setTextColor(connected ? Color.rgb(80,216,144) : Color.rgb(145,145,155));
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(48));
        sp.topMargin = dp(14);
        root.addView(status, sp);

        Space top = new Space(this);
        root.addView(top, new LinearLayout.LayoutParams(1, dp(20)));

        LinearLayout circle = new LinearLayout(this);
        circle.setOrientation(LinearLayout.VERTICAL);
        circle.setGravity(Gravity.CENTER);
        circle.setBackgroundResource(R.drawable.bg_button);

        TextView power = tv("⏻", 44);
        power.setGravity(Gravity.CENTER);
        circle.addView(power, new LinearLayout.LayoutParams(-1, dp(70)));

        connectText = tv(connected ? "DISCONNECT" : "CONNECT", 17);
        connectText.setTypeface(null, 1);
        connectText.setGravity(Gravity.CENTER);
        circle.addView(connectText);

        TextView hint = tv(connected ? "Tap to Stop" : "Tap to Start", 13);
        hint.setTextColor(Color.rgb(125,125,135));
        hint.setGravity(Gravity.CENTER);
        circle.addView(hint);

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(260), dp(260));
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(circle, cp);
        circle.setOnClickListener(v -> toggleVpn());

        Space mid = new Space(this);
        root.addView(mid, new LinearLayout.LayoutParams(1, dp(28)));

        TextView ready = tv(connected ? "Connected" : "Ready to Connect", 23);
        ready.setGravity(Gravity.CENTER);
        root.addView(ready);

        TextView sub = tv(connected ? "NET CLOUD SL is protecting your traffic" : "Tap the power button to connect", 14);
        sub.setTextColor(Color.rgb(145,145,155));
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        String active = prefs.getString("active_name", "No VLESS config selected");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(14),dp(18),dp(14));
        card.setBackgroundResource(R.drawable.bg_card);

        server = tv("🌐   VLESS    " + active, 16);
        server.setTypeface(null,1);
        card.addView(server);

        String host = prefs.getString("active_host", "Add a VLESS configuration");
        String port = prefs.getString("active_port", "443");
        TextView endpoint = tv("      " + host + ":" + port + "  •  TCP • TLS", 13);
        endpoint.setTextColor(Color.rgb(145,145,155));
        card.addView(endpoint);

        ping = tv("      — ms", 13);
        ping.setTextColor(Color.rgb(80,216,144));
        card.addView(ping);

        LinearLayout.LayoutParams cardp = new LinearLayout.LayoutParams(-1, dp(105));
        cardp.topMargin = dp(20);
        root.addView(card, cardp);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        String[] labels = {"⌂\nHome","◎\nConfigs","▥\nStatistics","⚙\nSettings"};
        for (int i=0;i<labels.length;i++) {
            TextView n=tv(labels[i],12);
            n.setGravity(Gravity.CENTER);
            n.setPadding(0,dp(8),0,dp(8));
            final int tab=i;
            n.setOnClickListener(v -> {
                if(tab==1) showConfigs();
                else if(tab==2) showStats();
                else if(tab==3) showSettings();
            });
            nav.addView(n,new LinearLayout.LayoutParams(0,dp(62),1));
        }
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(70));
        np.topMargin=dp(8);
        root.addView(nav,np);
    }

    void toggleVpn() {
        if (!connected && prefs.getString("active_uuid", "").isEmpty()) {
            Toast.makeText(this, "Add a VLESS config first", Toast.LENGTH_SHORT).show();
            showConfigs();
            return;
        }

        Intent prep = VpnService.prepare(this);
        if (prep != null) {
            startActivityForResult(prep, 100);
            return;
        }

        Intent i = new Intent(this, NetCloudVpnService.class);
        if (!connected) {
            startService(i);
            connected=true;
        } else {
            stopService(i);
            connected=false;
        }
        showHome();
    }

    void header(String title) {
        base();
        TextView h=tv("‹  "+title,22);
        h.setTypeface(null,1);
        h.setOnClickListener(v->showHome());
        root.addView(h,new LinearLayout.LayoutParams(-1,dp(60)));
    }

    void showConfigs() {
        header("Configs");

        TextView add=tv("+  Import VLESS URL",17);
        add.setPadding(dp(16),dp(18),dp(16),dp(18));
        add.setBackgroundResource(R.drawable.bg_card);
        root.addView(add);
        add.setOnClickListener(v -> importVlessDialog());

        TextView info=tv("\nSupported format:\nVLESS URI\n\nExample:\nvless://UUID@server.example.com:443?type=tcp&security=tls#Net%20Cloud%20SL",13);
        info.setTextColor(Color.rgb(145,145,155));
        root.addView(info);

        addConfigCards();
    }

    void addConfigCards() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(18), 0, 0);

        int count = prefs.getInt("count", 0);
        if (count == 0) {
            TextView empty = tv("No VLESS configurations yet.", 16);
            empty.setTextColor(Color.rgb(145,145,155));
            list.addView(empty);
        }

        for (int i=0; i<count; i++) {
            String id = prefs.getString("id_"+i, "");
            String name = prefs.getString("name_"+i, "VLESS Server");
            String host = prefs.getString("host_"+i, "");
            String port = prefs.getString("port_"+i, "443");

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16),dp(14),dp(16),dp(14));
            card.setBackgroundResource(R.drawable.bg_card);

            TextView title = tv("🌐  " + name, 16);
            title.setTypeface(null,1);
            card.addView(title);

            TextView details = tv(host + ":" + port + "  •  VLESS", 13);
            details.setTextColor(Color.rgb(145,145,155));
            card.addView(details);

            LinearLayout buttons = new LinearLayout(this);
            buttons.setGravity(Gravity.END);

            Button use = new Button(this);
            use.setText("USE");
            use.setOnClickListener(v -> {
                prefs.edit()
                    .putString("active_uuid", id)
                    .putString("active_name", name)
                    .putString("active_host", host)
                    .putString("active_port", port)
                    .apply();
                Toast.makeText(this, "Selected: " + name, Toast.LENGTH_SHORT).show();
                showHome();
            });

            Button del = new Button(this);
            del.setText("DELETE");
            final int index=i;
            del.setOnClickListener(v -> deleteConfig(index));

            buttons.addView(use);
            buttons.addView(del);
            card.addView(buttons);

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(145));
            cp.bottomMargin=dp(12);
            list.addView(card, cp);
        }
        root.addView(list);
    }

    void importVlessDialog() {
        final EditText input = new EditText(this);
        input.setHint("vless://UUID@host:443?...#Name");
        input.setSingleLine(false);
        input.setPadding(dp(16),dp(8),dp(16),dp(8));

        new AlertDialog.Builder(this)
            .setTitle("Import VLESS")
            .setMessage("Paste a VLESS URI below")
            .setView(input)
            .setPositiveButton("IMPORT", null)
            .setNegativeButton("CANCEL", null)
            .create();

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Import VLESS")
            .setView(input)
            .setPositiveButton("IMPORT", (d,w) -> {
                String uri = input.getText().toString().trim();
                importVless(uri);
            })
            .setNegativeButton("CANCEL", null)
            .create();
        dialog.show();
    }

    void importVless(String raw) {
        try {
            if (!raw.toLowerCase().startsWith("vless://"))
                throw new IllegalArgumentException("Not a VLESS URI");

            Uri u = Uri.parse(raw);
            String userInfo = u.getUserInfo();
            if (userInfo == null || userInfo.isEmpty())
                throw new IllegalArgumentException("Missing UUID");

            String uuid = userInfo;
            String host = u.getHost();
            int port = u.getPort() > 0 ? u.getPort() : 443;

            if (host == null || host.isEmpty())
                throw new IllegalArgumentException("Missing server host");

            String name = u.getFragment();
            if (name == null || name.isEmpty()) name = "VLESS " + host;
            name = Uri.decode(name);

            String params = u.getQuery() == null ? "" : u.getQuery();
            String type = queryParam(params, "type", "tcp");
            String security = queryParam(params, "security", "none");
            String sni = queryParam(params, "sni", host);
            String path = queryParam(params, "path", "");
            String fp = queryParam(params, "fp", "");
            String pbk = queryParam(params, "pbk", "");
            String sid = queryParam(params, "sid", "");
            String flow = queryParam(params, "flow", "");
            String alpn = queryParam(params, "alpn", "");
            String allowInsecure = queryParam(params, "allowInsecure", "0");

            int count = prefs.getInt("count", 0);
            String id = UUID.randomUUID().toString();

            SharedPreferences.Editor e = prefs.edit();
            e.putInt("count", count + 1);
            e.putString("id_"+count, id);
            e.putString("name_"+count, name);
            e.putString("uuid_"+count, uuid);
            e.putString("host_"+count, host);
            e.putString("port_"+count, String.valueOf(port));
            e.putString("type_"+count, type);
            e.putString("security_"+count, security);
            e.putString("sni_"+count, sni);
            e.putString("path_"+count, path);
            e.putString("fp_"+count, fp);
            e.putString("pbk_"+count, pbk);
            e.putString("sid_"+count, sid);
            e.putString("flow_"+count, flow);
            e.putString("alpn_"+count, alpn);
            e.putString("allowInsecure_"+count, allowInsecure);

            // Make the newly imported config active.
            e.putString("active_uuid", id);
            e.putString("active_name", name);
            e.putString("active_host", host);
            e.putString("active_port", String.valueOf(port));
            e.apply();

            Toast.makeText(this, "VLESS imported successfully", Toast.LENGTH_SHORT).show();
            showConfigs();

        } catch (Exception ex) {
            new AlertDialog.Builder(this)
                .setTitle("Import failed")
                .setMessage("Invalid VLESS URI.\n\n" + ex.getMessage())
                .setPositiveButton("OK", null)
                .show();
        }
    }

    String queryParam(String query, String key, String fallback) {
        for (String p : query.split("&")) {
            if (p.startsWith(key+"=")) {
                String value = p.substring((key+"=").length());
                return Uri.decode(value);
            }
        }
        return fallback;
    }

    void deleteConfig(int index) {
        int count = prefs.getInt("count", 0);
        if (index < 0 || index >= count) return;

        SharedPreferences.Editor e = prefs.edit();
        for (int i=index; i<count-1; i++) {
            String[] keys={"id","name","uuid","host","port","type","security","sni","path","fp","pbk","sid","flow","alpn","allowInsecure"};
            for (String k:keys) e.putString(k+"_"+i, prefs.getString(k+"_"+(i+1), ""));
        }
        String[] keys={"id","name","uuid","host","port","type","security","sni","path","fp","pbk","sid","flow","alpn","allowInsecure"};
        for (String k:keys) e.remove(k+"_"+(count-1));
        e.putInt("count", count-1);
        e.apply();
        showConfigs();
    }

    void showStats() {
        header("Statistics");
        TextView s=tv("\nSession\n\n↓  0 B        ↑  0 B\n\nConnected time: 00:00:00\n\nPing: — ms",18);
        s.setTextColor(Color.rgb(230,230,235));
        root.addView(s);
    }

    void showSettings() {
        header("Settings");
        TextView s=tv("\nGeneral\n\n✓ Auto reconnect\n✓ Start on boot\n\nAbout\nNET CLOUD SL  •  v1.0",17);
        s.setPadding(dp(8),dp(15),dp(8),dp(15));
        root.addView(s);
    }
}
