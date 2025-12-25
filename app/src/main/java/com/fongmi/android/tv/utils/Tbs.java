package com.fongmi.android.tv.utils;

import android.os.Build;
import android.os.Environment;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.impl.X5WebViewCallback;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.utils.Path;
import com.orhanobut.logger.Logger;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.TbsCommonCode;
import com.tencent.smtt.sdk.TbsDownloader;
import com.tencent.smtt.sdk.TbsListener;
import com.tencent.smtt.export.external.TbsCoreSettings;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;

public class Tbs {
    private static final String TAG = Tbs.class.getSimpleName();

    private static boolean isCpu64Bit() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (abi.contains("64")) return true;
        }
        return false;
    }

    public static String getUrl() {
        String url = "https://gitee.com/cyh92/live/releases/download/release/x5.tbs.apk";
        File file = new File(Path.tv(), "x5.tbs.apk");
        if (file.exists()) return Server.get().getAddress("/file/TV/x5.tbs.apk");
        File x5 = new File(Path.download(), "x5.tbs.apk");
        if (x5.exists())
            return Server.get().getAddress("/file/" + Environment.DIRECTORY_DOWNLOADS + "/x5.tbs.apk");
        return url;//Server.get().getAddress("/x5.tbs.apk");
    }

    private static void tbsInit() {
        HashMap map = new HashMap();
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_PRIVATE_CLASSLOADER, true);
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER, true);
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE, true);
        QbSdk.initTbsSettings(map);
        TbsDownloader.stopDownload();
        QbSdk.PreInitCallback callback = new QbSdk.PreInitCallback() {
            @Override
            public void onViewInitFinished(boolean finished) {
                if (finished) Notify.show(R.string.x5webview_enabled);
            }

            @Override
            public void onCoreInitFinished() {
            }
        };
        QbSdk.initX5Environment(App.get(), callback);
    }

    public static void init() {
        if (Setting.getParseWebView() == 0) return;
        if (QbSdk.isTbsCoreInited()) return;
        App.post(() -> tbsInit());
    }

    public static String url() {
        String downloadUrl = "";
        if (isCpu64Bit()) {
            downloadUrl = "https://gitee.com/cyh92/live/releases/download/release/046279_arm64v8a_x5.tbs.apk";
        }else{
            downloadUrl = "https://gitee.com/cyh92/live/releases/download/release/046914_armeabi_x5.tbs.apk";
        }
        return downloadUrl;
    }

    public static File file() {
        File file = Path.cache("TBScore.apk");
        return file;
    }

    public static void remove() {
        File file = file();
        if (file.exists()) file.delete();
    }

    public static void install(X5WebViewCallback callback) {
        boolean canLoadX5 = QbSdk.canLoadX5(App.get());
        if (canLoadX5) return;
        HashMap map = new HashMap();
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_PRIVATE_CLASSLOADER, true);
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER, true);
        map.put(TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE, true);

        QbSdk.initTbsSettings(map);
        TbsListener tbsListener = new TbsListener() {

            /**
             * @param stateCode 用户可处理错误码请参考{@link TbsCommonCode}
             */
            @Override
            public void onDownloadFinish(int stateCode) {
                Logger.t(TAG).d("onDownloadFinish:" + stateCode);
            }

            /**
             * @param stateCode 用户可处理错误码请参考{@link TbsCommonCode}
             */
            @Override
            public void onInstallFinish(int stateCode) {
                Logger.t(TAG).d("onInstallFinish:" + stateCode);
                if (stateCode == TbsCommonCode.INSTALL_SUCCESS) callback.onX5Success();
                else callback.onX5Error();
            }

            /**
             * 首次安装应用，会触发内核下载，此时会有内核下载的进度回调。
             * @param progress 0 - 100
             */
            @Override
            public void onDownloadProgress(int progress) {
                Logger.t(TAG).d("onDownloadProgress:" + progress);
            }
        };
        QbSdk.setTbsListener(tbsListener);
        int version = isCpu64Bit() ? 46295 : 45912;
        QbSdk.reset(App.get());
        QbSdk.installLocalTbsCore(App.get(), version, file().getAbsolutePath());
    }

    private void initX5() {
        if (Build.VERSION.SDK_INT > 34) {
            Logger.t("提示").d("Android 版本大于 14，跳过 X5 内核初始化");
            return;
        }

        if (QbSdk.canLoadX5(App.get())) {
            Logger.t("提示").d("X5 内核已加载，跳过初始化");
            return;
        }
        String downloadUrl = null;
        if (isCpu64Bit()) {
            downloadUrl = "";
        }
        int version = isCpu64Bit() ? 46295 : 45912;
        if (downloadUrl == null) {
            Logger.t("提示").e("不支持的架构: ");
            Notify.show("X5不支持架构");
            return;
        }
        String apkName = "TBScore.apk";
        File filesDir = App.get().getFilesDir();
        if (filesDir == null) {
            Logger.t("提示").e("获取存储目录失败");
            return;
        }
        String apkDir = filesDir.getAbsolutePath();
        String apkPath = apkDir + File.separator + apkName;
        File file = new File(apkPath);
        try {
            if (file.exists()) {
                Logger.t("提示").i("APK 文件已存在，跳过下载");
            } else {
                Logger.t("提示").i("开始下载 Core APK: " + downloadUrl);
                Notify.show("正在远程下载X5Core，下载完成前请不要关闭应用");
                URL url = new URL(downloadUrl);
                URLConnection connection = url.openConnection();
                connection.connect();
                InputStream inputStream = connection.getInputStream();

                try (java.io.FileOutputStream outputStream = new java.io.FileOutputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                Notify.show("下载X5Core成功！");
                Logger.i("Core APK 下载完成: " + apkPath);
            }
            QbSdk.reset(App.get());
            QbSdk.installLocalTbsCore(App.get(), version, apkPath);
            QbSdk.initX5Environment(App.get(), new QbSdk.PreInitCallback() {
                @Override
                public void onViewInitFinished(boolean finished) {
                    if (finished) Notify.show(R.string.x5webview_enabled);
                }

                @Override
                public void onCoreInitFinished() {
                }
            });
        } catch (Exception e) {
            Logger.t("提示").e("Core APK 下载或加载失败: " + e.getMessage());
            Notify.show("获取X5Core失败，请使用系统WebView内核");
        }
    }
}
