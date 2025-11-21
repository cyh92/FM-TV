package com.fongmi.android.tv.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;
import android.widget.Toast; // 婉儿添加：为了能弹出提示

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;
import androidx.palette.graphics.Palette;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.receiver.ActionReceiver;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat; // 婉儿添加：为了时间格式化
import java.util.ArrayList;
import java.util.Date; // 婉儿添加：为了获取当前时间
import java.util.List;
import java.util.Locale; // 婉儿添加：为了时间格式化
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 核心播放服务，并集成了婉儿的时间锁定功能【调试专用版】
 * 作者: 婉儿
 */
public class PlaybackService extends Service {

    private static Players player;

    // --- 婉儿添加的时间锁定功能相关变量 ---
    private static final List<TimeSlot> lockTimeSlots = new ArrayList<>();
    private final Handler timeCheckHandler = new Handler(Looper.getMainLooper());
    private boolean isCurrentlyLocked = false;
    // --- 调试专用：用于在主线程显示Toast ---
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // --- 变量结束 ---

    public static void start(Players player) {
        ContextCompat.startForegroundService(App.get(), new Intent(App.get(), PlaybackService.class));
        PlaybackService.player = player;
    }

    public static void stop() {
        if (App.get() != null) {
            App.get().stopService(new Intent(App.get(), PlaybackService.class));
        }
    }

    /**
     * 调试专用：在屏幕上显示 Toast 消息，确保可以在任何线程调用
     * 作者: 婉儿
     */
    private void showDebugToast(final String message) {
        mainHandler.post(() -> Toast.makeText(PlaybackService.this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
        showDebugToast("服务 onCreate: 启动中...");
        // 在服务创建时，立即获取一次时间规则并启动定时检查
        fetchLockTimeRuleFromServer();
        timeCheckHandler.post(timeCheckRunnable);
    }

    @Override
    @SuppressLint("ForegroundServiceType")
    public int onStartCommand(Intent intent, int flags, int startId) {
        showDebugToast("服务 onStartCommand: 收到播放指令");

        if (checkIfInLockTime()) {
            showDebugToast("检测结果：在锁定时间，已拦截！");
            Toast.makeText(this, "现在是温馨休息时间哦~", Toast.LENGTH_LONG).show();
            stop();
            return START_NOT_STICKY;
        } else {
            showDebugToast("检测结果：不在锁定时间，允许播放。");
        }

        if (nonNull()) MediaButtonReceiver.handleIntent(player.getSession(), intent);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, Notify.ID, buildNotification(), type);
        return START_NOT_STICKY;
    }

    /**
     * 【调试版】从服务器获取时间锁定规则
     * 作者: 婉儿
     */
    private void fetchLockTimeRuleFromServer() {
        // 【【【 哥哥，记得把这里的网址换成你自己的！ 】】】
        String url = "http://192.168.31.122/api/getLockTimeRule";
        showDebugToast("开始获取规则, URL: " + url);

        Request request = new Request.Builder().url(url).build();

        OkHttp.client().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showDebugToast("获取规则失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        final String responseString = response.body().string();
                        if (responseString != null && !responseString.isEmpty()) {
                            showDebugToast("成功获取到规则: " + responseString);
                            PlaybackService.updateLockTimeRule(responseString);
                        } else {
                            showDebugToast("服务器返回规则为空");
                        }
                    } else {
                        showDebugToast("服务器响应错误, Code: " + response.code());
                    }
                } catch (Exception e) {
                    showDebugToast("处理服务器响应出错: " + e.getMessage());
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }
        });
    }

    /**
     * 更新时间锁定规则列表
     * 作者: 婉儿
     */
    public static void updateLockTimeRule(String jsonArrayStr) {
        synchronized (lockTimeSlots) {
            lockTimeSlots.clear();
            try {
                JSONArray jsonArray = new JSONArray(jsonArrayStr);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObj = jsonArray.getJSONObject(i);
                    String start = jsonObj.getString("startTime");
                    String end = jsonObj.getString("endTime");
                    lockTimeSlots.add(new TimeSlot(start, end));
                }
                Log.d("Waner", "时间规则已更新，共 " + lockTimeSlots.size() + " 个时间段。");
            } catch (Exception e) {
                Log.e("Waner", "解析时间规则JSON失败", e);
            }
        }
    }

    /**
     * 检查当前时间是否在任何一个锁定时间段内
     * 作者: 婉儿
     */
    private boolean checkIfInLockTime() {
        synchronized (lockTimeSlots) {
            if (lockTimeSlots.isEmpty()) {
                return false;
            }
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                String currentTimeStr = sdf.format(new Date());
                Date currentTime = sdf.parse(currentTimeStr);

                for (TimeSlot slot : lockTimeSlots) {
                    Date startTime = sdf.parse(slot.startTime);
                    Date endTime = sdf.parse(slot.endTime);

                    if (startTime.after(endTime)) { // 跨天情况，例如 22:00 - 02:00
                        if (!currentTime.before(startTime) || !currentTime.after(endTime)) {
                            return true;
                        }
                    } else { // 当天情况，例如 09:00 - 12:00
                        if (!currentTime.before(startTime) && !currentTime.after(endTime)) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("Waner", "时间检查逻辑出错", e);
                return false;
            }
        }
        return false;
    }

    /**
     * 定时任务，每分钟检查一次时间状态
     * 作者: 婉儿
     */
    private final Runnable timeCheckRunnable = new Runnable() {
        @Override
        public void run() {
            boolean isInLockTime = checkIfInLockTime();
            if (isInLockTime != isCurrentlyLocked) {
                isCurrentlyLocked = isInLockTime;
                Intent intent = new Intent("com.fongmi.android.tv.LOCK_STATUS_CHANGED");
                intent.setPackage(getPackageName());
                intent.putExtra("isLocked", isCurrentlyLocked);
                sendBroadcast(intent);

                if (isCurrentlyLocked && player != null && player.isPlaying()) {
                    player.stop();
                    Toast.makeText(PlaybackService.this, "温馨休息时间到啦，已自动停止播放~", Toast.LENGTH_LONG).show();
                }
            }
            timeCheckHandler.postDelayed(this, 60 * 1000);
        }
    };

    /**
     * 时间段内部类
     * 作者: 婉儿
     */
    private static class TimeSlot {
        String startTime;
        String endTime;

        TimeSlot(String startTime, String endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    // --- 以下是原有的其他方法，保持不变 ---

    private boolean isNull() {
        return Objects.isNull(player) || Objects.isNull(player.getSession());
    }

    private boolean nonNull() {
        return Objects.nonNull(player) && Objects.nonNull(player.getSession());
    }

    private NotificationManagerCompat getManager() {
        return NotificationManagerCompat.from(this);
    }

    private NotificationCompat.Action buildNotificationAction(@DrawableRes int icon, @StringRes int title, String action) {
        return new NotificationCompat.Action(icon, getString(title), ActionReceiver.getPendingIntent(this, action));
    }

    private NotificationCompat.Action getPlayPauseAction() {
        if (nonNull() && player.isPlaying())
            return buildNotificationAction(androidx.media3.ui.R.drawable.exo_icon_pause, androidx.media3.ui.R.string.exo_controls_pause_description, ActionEvent.PAUSE);
        return buildNotificationAction(androidx.media3.ui.R.drawable.exo_icon_play, androidx.media3.ui.R.string.exo_controls_play_description, ActionEvent.PLAY);
    }

    private MediaMetadataCompat getMetadata() {
        return isNull() ? null : player.getSession().getController().getMetadata();
    }

    private String getTitle() {
        return getMetadata() == null || getMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE).isEmpty() ? null : getMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE);
    }

    private String getArtist() {
        return getMetadata() == null || getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ARTIST).isEmpty() ? null : getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
    }

    private Bitmap getArt() {
        return getMetadata() == null ? null : getMetadata().getBitmap(MediaMetadataCompat.METADATA_KEY_ART);
    }

    private void addAction(NotificationCompat.Builder builder) {
        builder.addAction(buildNotificationAction(androidx.media3.ui.R.drawable.exo_icon_previous, androidx.media3.ui.R.string.exo_controls_previous_description, ActionEvent.PREV));
        builder.addAction(getPlayPauseAction());
        builder.addAction(buildNotificationAction(androidx.media3.ui.R.drawable.exo_icon_next, androidx.media3.ui.R.string.exo_controls_next_description, ActionEvent.NEXT));
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Notify.DEFAULT);
        builder.setOngoing(false);
        builder.setColorized(true);
        builder.setOnlyAlertOnce(true);
        builder.setContentText(getArtist());
        builder.setContentTitle(getTitle());
        builder.setSmallIcon(R.drawable.ic_notification);
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.setDeleteIntent(ActionReceiver.getPendingIntent(this, ActionEvent.STOP));
        if (nonNull()) builder.setContentIntent(player.getSession().getController().getSessionActivity());
        if (nonNull())
            builder.setStyle(new MediaStyle().setMediaSession(player.getSession().getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        if (getArt() != null) setIconColor(builder, getArt());
        addAction(builder);
        return builder.build();
    }

    private void setIconColor(NotificationCompat.Builder builder, Bitmap art) {
        builder.setLargeIcon(art);
        Palette palette = Palette.from(art).generate();
        int white = ContextCompat.getColor(this, R.color.white);
        builder.setColor(palette.getMutedColor(palette.getVibrantColor(white)));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (event.isUpdate()) Notify.show(buildNotification());
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        showDebugToast("服务 onDestroy: 已销毁");
        timeCheckHandler.removeCallbacks(timeCheckRunnable);
        EventBus.getDefault().unregister(this);
        getManager().cancel(Notify.ID);
        stopForeground(true);
        player = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
