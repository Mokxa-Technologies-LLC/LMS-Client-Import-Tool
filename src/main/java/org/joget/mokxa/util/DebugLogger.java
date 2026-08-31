package org.joget.mokxa.util;

import java.util.Map;

import org.joget.commons.util.LogUtil;

public final class DebugLogger {

    private static final ThreadLocal<Boolean> DEBUG_MODE = new ThreadLocal<>();

    private DebugLogger() {
    }

    public static void init(Map properties) {
        DEBUG_MODE.set(isEnabled(properties));
    }

    public static void clear() {
        DEBUG_MODE.remove();
    }

    public static void info(String source, String message) {
        if (isEnabled()) LogUtil.info(source, message);
    }

    public static void warn(String source, String message) {
        if (isEnabled()) LogUtil.warn(source, message);
    }

    public static void error(String source, Throwable throwable, String message) {
        LogUtil.error(source, throwable, message);
    }

    private static boolean isEnabled() {
        Boolean enabled = DEBUG_MODE.get();
        return enabled != null && enabled;
    }

    private static boolean isEnabled(Map properties) {
        if (properties == null) return false;

        Object debugMode = properties.get("debugMode");

        if (debugMode instanceof Boolean) return (Boolean) debugMode;
        if (debugMode instanceof String) return "true".equalsIgnoreCase((String) debugMode);

        return false;
    }
}