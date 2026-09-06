package com.netcloudsl.app;

import org.json.JSONObject;
import java.lang.reflect.Method;

/**
 * Runtime adapter for the official XTLS/libXray Android AAR.
 *
 * The app deliberately uses reflection so the project can still be opened and
 * built before libXray.aar is supplied. Once the AAR is present, the adapter
 * calls the gomobile LibXray.invoke() API.
 */
public final class XrayNativeBridge {
    private static final String[] LIB_CLASSES = {
            "libXray.LibXray",
            "com.xtls.libxray.LibXray"
    };
    private static Class<?> libClass;
    private static Method invokeMethod;
    private static Method setDnsMethod;
    private static Method resetDnsMethod;

    static {
        loadApi();
    }

    private XrayNativeBridge() {}

    private static void loadApi() {
        for (String name : LIB_CLASSES) {
            try {
                libClass = Class.forName(name);
                invokeMethod = libClass.getMethod("invoke", String.class);
                try { setDnsMethod = libClass.getMethod("setDNS", String.class); } catch (Throwable ignored) {}
                try { resetDnsMethod = libClass.getMethod("resetDNS"); } catch (Throwable ignored) {}
                return;
            } catch (Throwable ignored) {}
        }
    }

    public static boolean isAvailable() { return libClass != null && invokeMethod != null; }

    public static void setDns(String dns) throws Exception {
        if (setDnsMethod != null) setDnsMethod.invoke(null, dns);
    }

    public static String invoke(String request) throws Exception {
        if (!isAvailable()) throw new IllegalStateException(
                "libXray Android AAR is missing. Build/copy libXray.aar into app/libs/.");
        Object result = invokeMethod.invoke(null, request);
        return result == null ? "" : String.valueOf(result);
    }

    public static String run(String configJson) throws Exception {
        JSONObject request = new JSONObject()
                .put("apiVersion", 3)
                .put("method", "runXray")
                .put("payload", new JSONObject().put("xrayJson", configJson));
        return invoke(request.toString());
    }

    public static String stop() throws Exception {
        JSONObject request = new JSONObject()
                .put("apiVersion", 3)
                .put("method", "stopXray")
                .put("payload", new JSONObject());
        return invoke(request.toString());
    }

    public static boolean running() {
        try {
            JSONObject request = new JSONObject()
                    .put("apiVersion", 3)
                    .put("method", "getXrayState")
                    .put("payload", new JSONObject());
            JSONObject response = new JSONObject(invoke(request.toString()));
            return response.optBoolean("success", false)
                    && response.optJSONObject("data") != null
                    && response.optJSONObject("data").optBoolean("running", false);
        } catch (Throwable ignored) { return false; }
    }

    public static void resetDns() {
        try { if (resetDnsMethod != null) resetDnsMethod.invoke(null); } catch (Throwable ignored) {}
    }
}
