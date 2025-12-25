package com.fongmi.android.tv.ui.custom;

import static com.tencent.smtt.sdk.WebSettings.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewParent;

//import android.webkit.WebChromeClient;
//import android.webkit.WebSettings;
//import android.webkit.WebView;
//import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.fongmi.android.tv.bean.Result;
import com.tencent.smtt.export.external.interfaces.IX5WebChromeClient;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

import com.fongmi.android.tv.bean.Channel;
import com.orhanobut.logger.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class WebViewPlayer extends FrameLayout {

    private static final String TAG = "WebViewPlayer";
    public WebView webView;
    private ProgressBar progressBar;
    private View touchInterceptor;
    private boolean isUserInteractionEnabled = false; // 默认禁用用户交互
    private VideoPlayerCallback callback;
    private boolean isVideoDetected = false;
    
    public interface VideoPlayerCallback {
        void onPageStarted();
        void onPageFinished(WebView webView);
        void onPageLoadProgress(int progress);
    }

    public WebViewPlayer(Context context) {
        this(context, null);
    }

    public WebViewPlayer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WebViewPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    public void start(Result result) {
        isVideoDetected = false;
        Logger.t(TAG).d("Starting WebView with URL: " + result.getUrl());
        Logger.t(TAG).d("用户交互状态: " + (isUserInteractionEnabled ? "启用" : "禁用"));
        Logger.t(TAG).d("触摸透明状态: " + isTouchTransparent());
        
        webView.loadUrl(result.getUrl().v(), result.getHeader());
        // 添加显示动画
        webView.setAlpha(0f);
        webView.animate()
                .alpha(1f)
                .setDuration(300)
                .start();
        // 注入JavaScript禁用用户交互
        if (!isUserInteractionEnabled) {
            injectDisableInteractionScript();
        }
    }
    
    public void stop(){
        isVideoDetected = false;
        webView.stopLoading();
        webView.loadUrl("about:blank");
        Logger.t(TAG).d("WebView stopped");
    }
    
    public void setCallback(VideoPlayerCallback callback) {
        this.callback = callback;
    }
    
    public boolean isVideoDetected() {
        return isVideoDetected;
    }
    private void init(Context context) {
        initWebView(context);
        initProgressBar(context);
        initTouchInterceptor(context);
        addViewsToLayout();
        
        // 默认禁用交互，设置视图属性
        setClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
        Logger.t(TAG).d("初始化WebViewPlayer，默认禁用交互");
    }

    private void initWebView(Context context) {
        webView = new WebView(context);
        setupWebViewSettings();
        setDefaultWebClients();
    }

    private void setupWebViewSettings() {
        WebSettings settings = webView.getSettings();
        
        // Core JavaScript and DOM settings
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        
        // Performance optimizations for live streaming
        settings.setLoadsImagesAutomatically(false); // 禁用自动加载图片
        settings.setBlockNetworkImage(true); // 禁用网络图片加载
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE); // 直播不需要缓存
        
        // 禁用用户交互功能
        settings.setSupportZoom(false); // 禁用缩放
        settings.setBuiltInZoomControls(false); // 禁用内置缩放控件
        settings.setDisplayZoomControls(false); // 禁用显示缩放控件
        
        // Media playback settings
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        // Enhanced User-Agent for better compatibility
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36 Edg/137.0.0.0";
        settings.setUserAgentString(userAgent);
        
        // Security settings
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setDatabaseEnabled(false);
        settings.setGeolocationEnabled(false);
        
        // Mixed content handling for secure streaming
        settings.setMixedContentMode(2); // MIXED_CONTENT_ALWAYS_ALLOW = 2

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false);
        }
        
        // TV adaptation settings
        webView.setFocusable(false); // 禁用焦点，防止点击
        webView.setFocusableInTouchMode(false); // 禁用触摸模式下的焦点
        webView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        webView.setScrollbarFadingEnabled(true);
        
        // 禁用WebView内部的触摸处理，但不干扰事件传递
        webView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 不处理任何事件，让dispatchTouchEvent处理
                return false;
            }
        });
        
        Logger.t(TAG).d("WebView settings configured for live streaming");
    }

    private void setDefaultWebClients() {
        webView.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageStarted(WebView webView, String url, Bitmap favicon) {
                super.onPageStarted(webView, url, favicon);
                webView.setBackgroundColor(Color.BLACK); // 立即设置黑色背景
                if (callback != null) {
                    callback.onPageStarted();
                }
            }
            @Override
            public void onPageFinished(WebView webView, String url) {
                super.onPageFinished(webView, url);
                if (callback != null) {
                    callback.onPageFinished(webView);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    progressBar.setProgress(newProgress);
                    progressBar.setVisibility(newProgress == 100 ? View.GONE : View.VISIBLE);
                }
                
                if (callback != null) {
                    callback.onPageLoadProgress(newProgress);
                }
                
                Logger.t(TAG).d("Page load progress: " + newProgress + "%");
            }

        });
    }
    private void initProgressBar(Context context) {
        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(2)));
        progressBar.setMax(100);
        progressBar.setProgress(0);
    }

    private void initTouchInterceptor(Context context) {
        // 简化实现：不再需要额外的触摸拦截器
        // 直接通过WebViewPlayer的onTouchEvent处理
        touchInterceptor = null;
    }

    private void addViewsToLayout() {
        addView(webView);
        addView(progressBar);
        // touchInterceptor 不再需要，直接通过onTouchEvent处理
    }

    // 简化的触摸事件处理，依赖setClickable控制
    // 删除复杂的onInterceptTouchEvent和dispatchTouchEvent处理

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // 如果禁用用户交互，直接让父视图处理触摸事件
        if (!isUserInteractionEnabled) {
            Logger.t(TAG).d("禁用交互模式，跳过WebViewPlayer，直接让父视图处理: " + event.getAction());
            
            // 直接返回false，不处理任何触摸事件，让父视图处理
            return false;
        }
        
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 如果禁用用户交互，确保事件不被消费，让父视图处理
        if (!isUserInteractionEnabled) {
            Logger.t(TAG).d("禁用交互模式，不处理触摸事件: " + event.getAction());
            return false; // 不消费事件，让LiveActivity处理手势
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (webView != null && webView.dispatchKeyEvent(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (webView != null && webView.onKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    public void setUserInteractionEnabled(boolean enabled) {
        isUserInteractionEnabled = enabled;
        // 不再需要touchInterceptor，直接通过WebView设置处理
        if (webView != null) {
            webView.setFocusable(enabled);
            webView.setFocusableInTouchMode(enabled);
        }
        
        // 当禁用交互时，让WebViewPlayer对触摸事件透明
        if (!enabled) {
            setClickable(false);
            setFocusable(false);
            setFocusableInTouchMode(false);
        } else {
            setClickable(true);
            setFocusable(true);
            setFocusableInTouchMode(true);
        }
        
        Logger.t(TAG).d("用户交互设置: " + (enabled ? "启用" : "禁用") + ", 视图可点击: " + isClickable());
    }
    
    /**
     * 禁用用户交互（点击、触摸等）
     */
    public void disableUserInteraction() {
        setUserInteractionEnabled(false);
    }
    
    /**
     * 启用用户交互（点击、触摸等）
     */
    public void enableUserInteraction() {
        setUserInteractionEnabled(true);
    }
    
    /**
     * 获取当前用户交互状态
     */
    public boolean isUserInteractionEnabled() {
        return isUserInteractionEnabled;
    }
    
    /**
     * 检查WebViewPlayer是否对触摸事件透明
     */
    public boolean isTouchTransparent() {
        return !isClickable() && !isFocusable();
    }

    /**
     * 注入JavaScript禁用网页内容交互，但不影响LiveActivity手势
     */
    private void injectDisableInteractionScript() {
        String script = 
            "javascript:" +
            "(function(){" +
                // 只禁用网页内容的交互，不影响容器级别的手势
                "var style = document.createElement('style');" +
                "style.innerHTML = '* { " +
                    "-webkit-user-select: none !important; " +
                    "-webkit-touch-callout: none !important; " +
                    "-webkit-tap-highlight-color: rgba(0,0,0,0) !important; " +
                    "user-select: none !important; " +
                "} a, button, input, textarea { pointer-events: none !important; }';" +
                "document.head.appendChild(style);" +
                // 禁用特定事件，但不禁用触摸滑动
                "document.addEventListener('click', function(e){e.preventDefault(); e.stopPropagation();}, true);" +
                "document.addEventListener('contextmenu', function(e){e.preventDefault(); e.stopPropagation();}, true);" +
                "document.addEventListener('selectstart', function(e){e.preventDefault(); e.stopPropagation();}, true);" +
                "document.addEventListener('dragstart', function(e){e.preventDefault(); e.stopPropagation();}, true);" +
            "})()";
        
        webView.post(() -> {
            webView.loadUrl(script);
            Logger.t(TAG).d("已注入禁用WebView内容交互的JavaScript代码");
        });
    }

    public void onResume() {
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    public void onPause() {
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
    }

    public void destroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.setWebChromeClient(null);
            webView.destroy();
            webView = null;
        }
        removeAllViews();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}