package com.fongmi.android.tv.receiver;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.fongmi.android.tv.ui.activity.HomeActivity;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BootJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
