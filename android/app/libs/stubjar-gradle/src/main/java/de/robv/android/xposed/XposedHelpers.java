package de.robv.android.xposed;

public class XposedHelpers {
    public static Class<?> findClass(String className, ClassLoader classLoader) { return null; }
    public static java.lang.reflect.Method findMethodExact(Class<?> clazz, String methodName, Object... parameterTypes) { return null; }
    public static void findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {}
    public static Object getStaticObjectField(Class<?> clazz, String fieldName) { return null; }
    public static Object getObjectField(Object obj, String fieldName) { return null; }
    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) { return null; }
    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }
}
