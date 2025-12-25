package com.fongmi.android.tv.receiver;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.ui.activity.HomeActivity;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        registerCallback();
        autoStart(context,intent);
    }

    //开机自启动设置
    private void autoStart(Context context, Intent intent){
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            boolean isAutoStartEnabled= Setting.isAutoStart();

            if(isAutoStartEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ComponentName serviceComponent = new ComponentName(context, BootJobService.class);
                    JobInfo.Builder builder = new JobInfo.Builder(0, serviceComponent);
                    builder.setOverrideDeadline(0);
                    JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
                    jobScheduler.schedule(builder.build());

                }else {
                    // 启动你的应用主Activity或服务
                    Intent launchIntent = new Intent(context, HomeActivity.class);
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                }
            }
            // 或者启动服务
            // context.startService(new Intent(context, MyService.class));
        }
    }
    private void registerCallback() {
        ((ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE)).registerDefaultNetworkCallback(new Callback());
    }

    static class Callback extends ConnectivityManager.NetworkCallback {

        private boolean first;

        @Override
        public void onAvailable(@NonNull Network network) {
            if (first) doJob();
            else first = true;
        }

        @Override
        public void onLost(@NonNull Network network) {
        }

        private void doJob() {
            LiveConfig.get().init().load();
            ((ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(this);
        }
    }
}
