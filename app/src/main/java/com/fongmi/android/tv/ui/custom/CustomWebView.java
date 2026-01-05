package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.view.View;
//import android.webkit.SslErrorHandler;
//import android.webkit.WebResourceRequest;
//import android.webkit.WebResourceResponse;
//import android.webkit.WebSettings;
//import android.webkit.WebView;
//import android.webkit.WebViewClient;


import com.tencent.smtt.export.external.interfaces.SslError;
import com.tencent.smtt.export.external.interfaces.SslErrorHandler;
import com.tencent.smtt.export.external.interfaces.WebResourceRequest;
import com.tencent.smtt.export.external.interfaces.WebResourceResponse;
import com.tencent.smtt.sdk.CookieManager;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;



import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.ui.dialog.WebDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Sniffer;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class CustomWebView extends WebView implements DialogInterface.OnDismissListener {

    private static final String TAG = CustomWebView.class.getSimpleName();

    private static final Pattern PLAYER = Pattern.compile("player.*https?://");
    private static final String BLANK = "about:blank";
    private static final int MAX_URLS = 5;
    private static final long DIALOG_TIMEOUT = 60000; // 对话框超时时间: 60秒

    private final LinkedHashSet<String> urls;
    private final WebResourceResponse empty;
    private final AtomicBoolean stop;
    private final ReentrantLock urlsLock;
    private ParseCallback callback;
    private WebDialog dialog;
    private Runnable timer;
    private Runnable dialogTimer;
    private boolean detect;
    private String click;
    private String from;
    private String key;
    private String url;

    public static CustomWebView create(@NonNull Context context) {
        initTbs();
        return new CustomWebView(context);
    }

    private CustomWebView(@NonNull Context context) {
        super(context);
        stop = new AtomicBoolean(false);
        urls = new LinkedHashSet<>();
        urlsLock = new ReentrantLock();
        empty = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
        timer = () -> stop(true);
        dialogTimer = () -> stop(true);
        initSettings();
        showTbs();
    }
    private static void initTbs() {
        if (Setting.getParseWebView() == 0) QbSdk.forceSysWebView();
        else QbSdk.unForceSysWebView();
    }

    private void showTbs() {
        if (this.getIsX5Core())  Notify.show(R.string.x5webview_parsing);
    }
    @SuppressLint("SetJavaScriptEnabled")
    public void initSettings() {
        WebSettings setting = getSettings();
        setting.setSupportZoom(true);
        setting.setUseWideViewPort(true);
        setting.setDatabaseEnabled(true);
        setting.setDomStorageEnabled(true);
        setting.setJavaScriptEnabled(true);
        setting.setBuiltInZoomControls(true);
        setting.setDisplayZoomControls(false);
        setting.setLoadWithOverviewMode(true);
        setting.setUserAgentString(Setting.getUa());
        setting.setMediaPlaybackRequiresUserGesture(false);
        setting.setJavaScriptCanOpenWindowsAutomatically(false);
        setting.setMixedContentMode(2);
        setWebViewClient(webViewClient());
        setWebChromeClient(webChromeClient());
    }

    public CustomWebView start(String key, String from, Map<String, String> headers, String url, String click, ParseCallback callback, boolean detect) {
        SpiderDebug.log(TAG, "key=%s, from=%s, click=%s, url=%s, headers=%s", key, from, click, url, headers);
        App.post(timer, Constant.TIMEOUT_PARSE_WEB);
        this.callback = callback;
        this.detect = detect;
        this.click = click;
        this.from = from;
        this.key = key;
        this.url = url;
        start(headers);
        return this;
    }

    private void start(Map<String, String> headers) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
        checkHeader(url, headers);
        loadUrl(url, headers);
    }

    private void checkHeader(String url, Map<String, String> headers) {
        for (String key : headers.keySet()) {
            if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) getSettings().setUserAgentString(headers.get(key));
            if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) CookieManager.getInstance().setCookie(url, headers.get(key));
        }
    }

    private WebViewClient webViewClient() {
        return new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String host = request.getUrl().getHost();
                if (TextUtils.isEmpty(host) || isAd(host)) return empty;
                Map<String, String> headers = request.getRequestHeaders();
                if (url.contains("/cdn-cgi/challenge-platform/")) post(() -> showDialog());
                if (detect && PLAYER.matcher(url).find() && addUrl(url)) onParseAdd(headers, url);
                else if (isVideoFormat(url)) onParseSuccess(headers, url);
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.equals(BLANK)) return;
                evaluate(getScript(url), 0);
            }

            @Override
            @SuppressLint("WebViewClientOnReceivedSslError")
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        };
    }
    private WebChromeClient webChromeClient() {
        return new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {
                try {
                    return BitmapFactory.decodeResource(App.get().getResources(), R.drawable.ic_logo);
                } catch (Throwable e) {
                    return super.getDefaultVideoPoster();
                }
            }
        };
    }
    private boolean addUrl(String url) {
        urlsLock.lock();
        try {
            if (urls.size() > MAX_URLS) urls.clear();
            return urls.add(url);
        } finally {
            urlsLock.unlock();
        }
    }

    private void showDialog() {
        if (dialog != null || App.activity() == null) return;
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
        dialog = new WebDialog(this).show();
        App.removeCallbacks(timer);
        App.post(dialogTimer, DIALOG_TIMEOUT); // 对话框也设置超时
    }

    private void hideDialog() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        stop(true);
    }

    private List<String> getScript(String url) {
        List<String> scripts = Sniffer.getScript(Uri.parse(url));
        if (scripts == null) scripts = new ArrayList<>();
        List<String> script = new ArrayList<>(scripts);
        if (TextUtils.isEmpty(click) || script.contains(click)) return script;
        script.add(0, click);
        return script;
    }

    private void evaluate(List<String> script, int index) {
        if (index >= script.size()) return;
        String js = script.get(index);
        if (TextUtils.isEmpty(js)) {
            evaluate(script, index + 1);
        } else {
            evaluateJavascript(js, value -> evaluate(script, index + 1));
        }
    }

    private boolean isAd(String host) {
        for (String ad : VodConfig.get().getAds()) if (Util.containOrMatch(host, ad)) return true;
        for (String ad : LiveConfig.get().getAds()) if (Util.containOrMatch(host, ad)) return true;
        return false;
    }

    private boolean isVideoFormat(String url) {
        try {
            if (!detect && url.equals(this.url)) return false;
            Spider spider = VodConfig.get().getSite(key).spider();
            if (spider.manualVideoCheck()) return spider.isVideoFormat(url);
            return Sniffer.isVideoFormat(url);
        } catch (Exception e) {
            SpiderDebug.log(TAG, "isVideoFormat error: %s", e.getMessage());
            return Sniffer.isVideoFormat(url);
        }
    }

    private void onParseAdd(Map<String, String> headers, String url) {
        post(() -> CustomWebView.create(App.get()).start(key, from, headers, url, click, callback, false));
    }

    private void onParseSuccess(Map<String, String> headers, String url) {
        ParseCallback cb = callback; // 保存引用
        callback = null; // 先置空,防止并发问题
        if (cb != null) cb.onParseSuccess(headers, url, from);
        post(() -> stop(false));
    }

    private void onParseError() {
        if (callback != null) callback.onParseError();
        callback = null;
    }

    public void stop(boolean error) {
        if (stop.get()) return;
        if (stop.compareAndSet(false, true)) {
            hideDialog();
            stopLoading();
            loadUrl(BLANK);
            App.removeCallbacks(timer);
            App.removeCallbacks(dialogTimer);
            if (error) onParseError();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop(true); // 确保在视图分离时清理资源
    }
}
