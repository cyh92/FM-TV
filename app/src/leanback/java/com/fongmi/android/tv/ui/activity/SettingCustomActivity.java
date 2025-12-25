package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.databinding.ActivitySettingCustomBinding;
import com.fongmi.android.tv.impl.X5WebViewCallback;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.X5WebViewDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.tencent.smtt.sdk.QbSdk;

public class SettingCustomActivity extends BaseActivity implements X5WebViewCallback {
    private ActivitySettingCustomBinding mBinding;
    private String[] parseWebview = {"系统", "X5 WebView"};

    private String[] historyText = {"开启", "关闭"};

    private String[] homeUI;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingCustomBinding.inflate(getLayoutInflater());
    }

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingCustomActivity.class));
    }

    @Override
    protected void initView() {
        mBinding.homeUI.requestFocus();
        mBinding.parseWebviewText.setText(parseWebview[Setting.getParseWebView()]);
        mBinding.homeHistoryText.setText(Setting.isHomeHistory()? historyText[0] : historyText[1]);
        mBinding.autoStartText.setText(Setting.isAutoStart()? "开启" : "关闭");
        mBinding.homeUIText.setText((homeUI = ResUtil.getStringArray(R.array.select_home_ui))[Setting.getHomeUI()]);
    }

    @Override
    protected void initEvent() {
        mBinding.parseWebview.setOnClickListener(this::setParseWebview);
        mBinding.homeHistory.setOnClickListener(this::setHomeHistory);
        mBinding.autoStart.setOnClickListener(this::setAutoStart);
        mBinding.homeUI.setOnClickListener(this::setHomeUI);
    }

    //首页UI
    private void setHomeUI(View view) {
        int index = Setting.getHomeUI();
        Setting.putHomeUI(index = index == homeUI.length - 1 ? 0 : ++index);
        mBinding.homeUIText.setText(homeUI[index]);
    }

    //设置开机自启动
    private void setAutoStart(View view) {
        Setting.putAutoStart(!Setting.isAutoStart());
        mBinding.autoStartText.setText(Setting.isAutoStart()? "开启" : "关闭");
    }
    //设置首页“最近观看”显示/隐藏
    private void setHomeHistory(View view) {
        Setting.putHomeHistory(!Setting.isHomeHistory());
        mBinding.homeHistoryText.setText(Setting.isHomeHistory()? historyText[0] : historyText[1]);
    }
    //设置嗅探浏览器内核
    private void setParseWebview(View view) {
        if (Build.VERSION.SDK_INT > 34) {
            Notify.show("Android 版本大于 14，跳过 X5 内核初始化");
            return;
        }else {
            int index = Setting.getParseWebView();
            int i= index == parseWebview.length - 1 ? 0 : ++index;
            Setting.putParseWebView(i);
            mBinding.parseWebviewText.setText(parseWebview[i]);
            if (index == 1 && QbSdk.getTbsVersion(App.get()) <= 0) X5WebViewDialog.create(this).show();
        }
    }


    @Override
    public void onX5Success() {
        int index = 1;
        Setting.putParseWebView(index);
        mBinding.parseWebviewText.setText(parseWebview[index]);
        App.post(() -> Util.restartApp(this), 500);
    }

    @Override
    public void onX5Error() {
        int index = 0;
        Setting.putParseWebView(index);
        mBinding.parseWebviewText.setText(parseWebview[index]);
    }

    @Override
    public void onX5Cancel() {
        int index = 0;
        Setting.putParseWebView(index);
        mBinding.parseWebviewText.setText(parseWebview[index]);
    }

}
