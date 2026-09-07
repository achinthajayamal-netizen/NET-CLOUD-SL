package com.netcloudsl.app;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Builds an Xray config for the selected VLESS profile and Android TUN FD. */
public final class XrayConfigGenerator {
    private XrayConfigGenerator() {}

    public static String build(SharedPreferences p, int index) throws Exception {
        String uuid = p.getString("uuid_"+index, "");
        String host = p.getString("host_"+index, "");
        int port = Integer.parseInt(p.getString("port_"+index, "443"));
        String type = p.getString("type_"+index, "tcp");
        String security = p.getString("security_"+index, "none");
        String sni = p.getString("sni_"+index, host);
        String path = p.getString("path_"+index, "");
        String fp = p.getString("fp_"+index, "");
        String pbk = p.getString("pbk_"+index, "");
        String sid = p.getString("sid_"+index, "");
        String flow = p.getString("flow_"+index, "");
        String alpn = p.getString("alpn_"+index, "");
        boolean allowInsecure = "1".equals(p.getString("allowInsecure_"+index, "0")) ||
                "true".equalsIgnoreCase(p.getString("allowInsecure_"+index, "false"));

        if (uuid.isEmpty() || host.isEmpty()) throw new IllegalArgumentException("Incomplete VLESS profile");

        JSONObject root = new JSONObject();
        JSONObject log = new JSONObject().put("loglevel", "warning");
        root.put("log", log);
        // libXray reads the Android VpnService TUN fd from the Xray root env.
        // NetCloudVpnService replaces the placeholder with the real fd at runtime.
        root.put("env", new JSONObject().put("xray.tun.fd", -1));

        JSONObject tunSettings = new JSONObject()
                .put("name", "netcloud0")
                .put("mtu", 1500)
                .put("gateway", new JSONArray().put("10.8.0.1/24").put("fd00:netc::1/64"))
                .put("dns", new JSONArray().put("1.1.1.1").put("8.8.8.8"));
        JSONObject tun = new JSONObject()
                .put("protocol", "tun")
                .put("settings", tunSettings)
                .put("tag", "tun-in");
        root.put("inbounds", new JSONArray().put(tun));

        JSONObject vlessSettings = new JSONObject()
                .put("address", host)
                .put("port", port)
                .put("id", uuid)
                .put("encryption", "none");
        if (!flow.isEmpty()) vlessSettings.put("flow", flow);

        JSONObject stream = new JSONObject()
                .put("network", type)
                .put("security", security);

        if ("tls".equalsIgnoreCase(security)) {
            JSONObject tls = new JSONObject()
                    .put("serverName", sni.isEmpty() ? host : sni)
                    .put("allowInsecure", allowInsecure);
            if (!fp.isEmpty()) tls.put("fingerprint", fp);
            if (!alpn.isEmpty()) tls.put("alpn", csvArray(alpn));
            stream.put("tlsSettings", tls);
        } else if ("reality".equalsIgnoreCase(security)) {
            JSONObject reality = new JSONObject()
                    .put("serverName", sni.isEmpty() ? host : sni)
                    .put("fingerprint", fp.isEmpty() ? "chrome" : fp);
            if (!pbk.isEmpty()) reality.put("publicKey", pbk);
            if (!sid.isEmpty()) reality.put("shortId", sid);
            stream.put("realitySettings", reality);
        }

        if ("ws".equalsIgnoreCase(type) || "websocket".equalsIgnoreCase(type)) {
            JSONObject ws = new JSONObject().put("path", path.isEmpty() ? "/" : path);
            if (!sni.isEmpty()) ws.put("host", sni);
            stream.put("wsSettings", ws);
        }
        if ("grpc".equalsIgnoreCase(type)) {
            JSONObject grpc = new JSONObject().put("serviceName", path);
            stream.put("grpcSettings", grpc);
        }

        JSONObject outbound = new JSONObject()
                .put("protocol", "vless")
                .put("settings", new JSONObject().put("vnext", new JSONArray().put(vlessSettings)))
                .put("streamSettings", stream)
                .put("tag", "proxy");
        root.put("outbounds", new JSONArray()
                .put(outbound)
                .put(new JSONObject().put("protocol", "freedom").put("tag", "direct"))
                .put(new JSONObject().put("protocol", "blackhole").put("tag", "block")));

        JSONObject routing = new JSONObject().put("domainStrategy", "AsIs");
        JSONArray rules = new JSONArray();
        rules.put(new JSONObject().put("type", "field").put("inboundTag", new JSONArray().put("tun-in")).put("outboundTag", "proxy"));
        routing.put("rules", rules);
        root.put("routing", routing);
        return root.toString(2);
    }

    private static JSONArray csvArray(String value) {
        JSONArray a = new JSONArray();
        for (String s : value.split(",")) if (!s.trim().isEmpty()) a.put(s.trim());
        return a;
    }
}
