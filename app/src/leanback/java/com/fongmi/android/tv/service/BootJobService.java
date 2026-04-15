package com.fongmi.android.tv.service;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import com.fongmi.android.tv.ui.activity.HomeActivity;

public class BootJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        // 安卓10+ 开机启动主页面
        Intent launchIntent = new Intent(getApplicationContext(), HomeActivity.class);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launchIntent);

        // 任务完成
        jobFinished(params, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}