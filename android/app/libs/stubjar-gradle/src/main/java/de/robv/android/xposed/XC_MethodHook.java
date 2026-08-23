package de.robv.android.xposed;

public abstract class XC_MethodHook {
    public void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    public void afterHookedMethod(MethodHookParam param) throws Throwable {}
    public static class MethodHookParam {
        public Object thisObject;
        public Class<?> declaringClass;
        public java.lang.reflect.Method method;
        public Object[] args;
        public Object result;
        public Throwable throwable;
        public boolean hasThrowable() { return false; }
        public void setResult(Object result) {}
    }
}
