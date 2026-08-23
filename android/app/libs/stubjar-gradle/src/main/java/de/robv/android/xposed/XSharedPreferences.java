package de.robv.android.xposed;

import java.io.File;

/**
 * Stub of XSharedPreferences for compilation (no Android SDK on stub classpath,
 * so it intentionally does NOT implement android.content.SharedPreferences).
 * The real class in the Xposed framework implements SharedPreferences and provides
 * these exact method signatures — binary compatible at runtime.
 */
public class XSharedPreferences {
    public File file;

    public XSharedPreferences(String packageName, String prefFileName) {
        this.file = new File("/data/data/" + packageName + "/shared_prefs/" + prefFileName + ".xml");
    }

    public void reload() {}

    public String getString(String key, String defValue) { return defValue; }
    public boolean getBoolean(String key, boolean defValue) { return defValue; }
    public int getInt(String key, int defValue) { return defValue; }
    public long getLong(String key, long defValue) { return defValue; }
    public float getFloat(String key, float defValue) { return defValue; }
}
