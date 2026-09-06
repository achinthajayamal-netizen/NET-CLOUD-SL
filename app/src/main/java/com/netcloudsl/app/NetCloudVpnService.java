package com.netcloudsl.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real VPN lifecycle: Android TUN -> libXray -> VLESS outbound. */
public class NetCloudVpnService extends VpnService {
    private ParcelFileDescriptor vpnInterface;
    private Thread runner;
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if ("STOP".equals(intent != null ? intent.getAction() : null)) {
            stopCore();
            stopSelf();
            return START_NOT_STICKY;
        }
        stopCore();
        stopping.set(false);
        try {
            if (!XrayNativeBridge.isAvailable()) {
                throw new IllegalStateException("libXray AAR missing");
            }

            SharedPreferences p = getSharedPreferences("netcloud_configs", MODE_PRIVATE);
            int index = findActiveIndex(p);
            if (index < 0) throw new IllegalStateException("No active VLESS profile");

            Builder b = new Builder()
                    .setSession("NET CLOUD SL")
                    .setMtu(1500)
                    .addAddress("10.8.0.2", 24)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .addRoute("0.0.0.0", 0)
                    .addRoute("::", 0);
            vpnInterface = b.establish();
            if (vpnInterface == null) throw new IllegalStateException("VPN interface creation failed");

            String config = XrayConfigGenerator.build(p, index);
            JSONObject root = new JSONObject(config);
            root.put("env", new JSONObject().put("xray.tun.fd", vpnInterface.getFd()));
            config = root.toString();

            // Keep DNS resolution outside the VPN loop where supported by libXray.
            try { XrayNativeBridge.setDns("1.1.1.1:53"); } catch (Throwable ignored) {}

            final String finalConfig = config;
            runner = new Thread(() -> {
                try {
                    String response = XrayNativeBridge.run(finalConfig);
                    JSONObject r = new JSONObject(response);
                    if (!r.optBoolean("success", false)) {
                        throw new IllegalStateException(r.optString("error", "Xray start failed"));
                    }
                    broadcast("CONNECTED");
                    while (!stopping.get() && XrayNativeBridge.running()) Thread.sleep(1000);
                } catch (Throwable e) {
                    if (!stopping.get()) broadcast("ERROR: " + e.getMessage());
                } finally {
                    stopCore();
                }
            }, "NetCloud-Xray");
            runner.start();
        } catch (Throwable e) {
            broadcast("ERROR: " + e.getMessage());
            stopCore();
        }
        return START_NOT_STICKY;
    }

    private int findActiveIndex(SharedPreferences p) {
        String active = p.getString("active_uuid", "");
        int count = p.getInt("count", 0);
        for (int i = 0; i < count; i++) if (active.equals(p.getString("id_"+i, ""))) return i;
        return -1;
    }

    private synchronized void stopCore() {
        if (stopping.getAndSet(true)) return;
        try { if (XrayNativeBridge.isAvailable()) XrayNativeBridge.stop(); } catch (Throwable ignored) {}
        XrayNativeBridge.resetDns();
        try { if (vpnInterface != null) vpnInterface.close(); } catch (Exception ignored) {}
        vpnInterface = null;
        broadcast("DISCONNECTED");
    }

    private void broadcast(String msg) {
        Intent i = new Intent("com.netcloudsl.app.VPN_STATUS");
        i.setPackage(getPackageName());
        i.putExtra("status", msg);
        sendBroadcast(i);
    }

    @Override public void onDestroy() { stopCore(); super.onDestroy(); }
}
