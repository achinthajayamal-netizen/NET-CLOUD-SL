package com.netcloudsl.app;

import android.net.VpnService;
import org.json.JSONObject;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Runtime adapter for the official XTLS/libXray Android AAR. */
public final class XrayNativeBridge {
    private static final String[] LIB_CLASSES = {
            "libXray.LibXray",
            "com.xtls.libxray.LibXray"
    };
    private static Class<?> libClass;
    private static Method invokeMethod;
    private static Method setDnsMethod;
    private static Method resetDnsMethod;
    private static Method registerDialerMethod;
    private static Method registerListenerMethod;
    private static Object controller;

    static { loadApi(); }
    private XrayNativeBridge() {}

    private static void loadApi() {
        for (String name : LIB_CLASSES) {
            try {
                libClass = Class.forName(name);
                invokeMethod = libClass.getMethod("invoke", String.class);
                for (Method m : libClass.getMethods()) {
                    String n = m.getName();
                    if (n.equals("setDNS") && m.getParameterTypes().length == 2) setDnsMethod = m;
                    if (n.equals("resetDNS") && m.getParameterTypes().length == 0) resetDnsMethod = m;
                    if (n.equals("registerDialerController") && m.getParameterTypes().length == 1) registerDialerMethod = m;
                    if (n.equals("registerListenerController") && m.getParameterTypes().length == 1) registerListenerMethod = m;
                }
                return;
            } catch (Throwable ignored) {}
        }
    }

    public static boolean isAvailable() { return libClass != null && invokeMethod != null; }

    /** Registers the Android VpnService protect(fd) callback exposed by current libXray. */
    public static synchronized void registerAndroidController(final VpnService vpn) throws Exception {
        if (!isAvailable()) throw new IllegalStateException("libXray AAR is missing");
        if (registerDialerMethod == null) throw new IllegalStateException("libXray Android controller API not found");
        Class<?> iface = registerDialerMethod.getParameterTypes()[0];
        if (!iface.isInterface()) throw new IllegalStateException("Invalid libXray controller type");
        if (controller == null) {
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if (name.equalsIgnoreCase("protectFd")) {
                    if (args == null || args.length == 0 || !(args[0] instanceof Number)) return false;
                    return vpn.protect(((Number) args[0]).intValue());
                }
                if (name.equals("toString")) return "NET CLOUD SL DialerController";
                if (name.equals("hashCode")) return System.identityHashCode(proxy);
                if (name.equals("equals")) return proxy == (args == null ? null : args[0]);
                return null;
            };
            controller = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler);
        }
        registerDialerMethod.invoke(null, controller);
        if (registerListenerMethod != null) registerListenerMethod.invoke(null, controller);
        if (setDnsMethod != null) setDnsMethod.invoke(null, controller, "1.1.1.1:53");
    }

    public static String invoke(String request) throws Exception {
        if (!isAvailable()) throw new IllegalStateException("libXray Android AAR is missing. Build/copy libXray.aar into app/libs/.");
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
