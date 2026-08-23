package de.robv.android.xposed.callbacks;

public abstract class XC_LoadPackage implements Comparable<XC_LoadPackage> {
    public static class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public boolean isFirstApplication;
    }
}
