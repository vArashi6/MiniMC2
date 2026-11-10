package de.minibits.client;

/** Erkennt, ob Iris ein Shader-Paket verwendet (ohne feste Abhängigkeit von Iris). */
public final class ShaderDetect {
    private static boolean tried;
    private static Object api;
    private static java.lang.reflect.Method inUse;

    public static boolean active() {
        if (!tried) {
            tried = true;
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = c.getMethod("getInstance").invoke(null);
                inUse = c.getMethod("isShaderPackInUse");
            } catch (Throwable t) {
                inUse = null;
            }
        }
        if (inUse == null) return false;
        try {
            return (Boolean) inUse.invoke(api);
        } catch (Throwable t) {
            return false;
        }
    }
}
