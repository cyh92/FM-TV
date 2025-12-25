package com.fongmi.android.tv.utils;

public class Github {

    public static final String downloadURL = "https://gitee.com/cyh92/live/releases/download";
    public static final String releaseURL = "https://gitee.com/cyh92/live/raw/main";
    private static String getUrl(String name) {
        return downloadURL + "/release/" + name;
    }
    public static String getJson(String name) {
        return releaseURL + "/release/" + name + ".json";
    }

    public static String getApk(String name) {
        return getUrl(name + ".apk");
    }
}
