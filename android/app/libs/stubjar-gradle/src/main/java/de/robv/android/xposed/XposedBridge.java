package de.robv.android.xposed;

public class XposedBridge {
    public static void log(String msg) {}
    public static void log(Throwable t) {}
    public static void hookMethod(java.lang.reflect.Method m, XC_MethodHook callback) {}
    public static void hookAllMethods(Class<?> clazz, String methodName, XC_MethodHook callback) {}
    public static void hookAllConstructors(Class<?> clazz, XC_MethodHook callback) {}
    public static Object invokeOriginalMethod(java.lang.reflect.Method method, Object thisObject, Object[] args) throws Throwable {
        return null;
    }
}
