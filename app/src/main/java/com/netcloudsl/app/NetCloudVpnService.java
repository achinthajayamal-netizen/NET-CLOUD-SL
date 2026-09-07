package com.netcloudsl.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** NET CLOUD SL V2: Android TUN -> libXray -> VLESS with socket protection and real connectivity verification. */
public class NetCloudVpnService extends VpnService {
    public static final String ACTION_STATUS = "com.netcloudsl.app.VPN_STATUS";
    private ParcelFileDescriptor vpnInterface;
    private Thread runner;
    private final AtomicBoolean stopping = new AtomicBoolean(true);

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if ("STOP".equals(intent != null ? intent.getAction() : null)) {
            stopCore(); stopSelf(); return START_NOT_STICKY;
        }
        stopCore();
        stopping.set(false);
        broadcast("CONNECTING");
        try {
            if (!XrayNativeBridge.isAvailable()) throw new IllegalStateException("Xray core is unavailable");
            SharedPreferences p = getSharedPreferences("netcloud_configs", MODE_PRIVATE);
            int index = findActiveIndex(p);
            if (index < 0) throw new IllegalStateException("No active VLESS profile");

            Builder b = new Builder().setSession("NET CLOUD SL V2").setMtu(1500)
                    .addAddress("10.8.0.2", 24)
                    .addDnsServer("1.1.1.1").addDnsServer("8.8.8.8")
                    .addRoute("0.0.0.0", 0).addRoute("::", 0);
            vpnInterface = b.establish();
            if (vpnInterface == null) throw new IllegalStateException("VPN interface creation failed");

            XrayNativeBridge.registerAndroidController(this);
            String config = XrayConfigGenerator.build(p, index);
            JSONObject root = new JSONObject(config);
            root.put("env", new JSONObject().put("xray.tun.fd", vpnInterface.getFd()));
            final String finalConfig = root.toString();

            runner = new Thread(() -> {
                try {
                    JSONObject response = new JSONObject(XrayNativeBridge.run(finalConfig));
                    if (!response.optBoolean("success", false))
                        throw new IllegalStateException(response.optString("error", "Xray start failed"));
                    broadcast("VERIFYING");
                    if (!verifyInternet()) throw new IllegalStateException("Tunnel started, but Internet traffic could not be verified");
                    broadcast("CONNECTED");
                    while (!stopping.get() && XrayNativeBridge.running()) Thread.sleep(1000);
                } catch (Throwable e) {
                    if (!stopping.get()) broadcast("ERROR: " + safeMessage(e));
                } finally { stopCore(); }
            }, "NetCloud-Xray-V2");
            runner.start();
        } catch (Throwable e) {
            broadcast("ERROR: " + safeMessage(e));
            stopCore();
        }
        return START_NOT_STICKY;
    }

    private boolean verifyInternet() {
        String[] urls = {"https://www.gstatic.com/generate_204", "https://www.google.com/generate_204", "https://1.1.1.1/cdn-cgi/trace"};
        for (String value : urls) {
            if (stopping.get()) return false;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(value).openConnection();
                c.setConnectTimeout(5000); c.setReadTimeout(5000); c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", "NET-CLOUD-SL-V2");
                int code = c.getResponseCode();
                if (code >= 200 && code < 400) return true;
            } catch (Throwable ignored) { } finally { if (c != null) c.disconnect(); }
        }
        return false;
    }

    private String safeMessage(Throwable e) {
        String s = e.getMessage();
        return (s == null || s.isEmpty()) ? e.getClass().getSimpleName() : s;
    }

    private int findActiveIndex(SharedPreferences p) {
        String active = p.getString("active_uuid", "");
        int count = p.getInt("count", 0);
        for (int i = 0; i < count; i++) if (active.equals(p.getString("id_" + i, ""))) return i;
        return -1;
    }

    private synchronized void stopCore() {
        stopping.set(true);
        try { if (XrayNativeBridge.isAvailable()) XrayNativeBridge.stop(); } catch (Throwable ignored) {}
        XrayNativeBridge.resetDns();
        try { if (vpnInterface != null) vpnInterface.close(); } catch (Exception ignored) {}
        vpnInterface = null;
        broadcast("DISCONNECTED");
    }

    private void broadcast(String msg) {
        Intent i = new Intent(ACTION_STATUS); i.setPackage(getPackageName()); i.putExtra("status", msg); sendBroadcast(i);
    }
    @Override public void onDestroy() { stopCore(); super.onDestroy(); }
}
