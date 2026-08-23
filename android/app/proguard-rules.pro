# Xposed 相关类由框架提供，避免混淆
-keep class de.robv.android.xposed.** { *; }
-keep class com.aisocial.agent.MainHook { *; }
-keepclasseswithmembers class * {
    void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam);
}
