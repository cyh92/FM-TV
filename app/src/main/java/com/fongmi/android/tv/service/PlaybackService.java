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

import androidx.annotation.DrawableRes;
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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

public class PlaybackService extends Service {

    private static Players player;

    // 作者: 婉儿
    // --- 时间控制核心模块 START ---
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isCurrentlyLocked = false;

    // 【【核心大改造】】
    // 用一个列表来存储所有的时间段规则
    private static final List<TimeSlot> lockTimeSlots = new ArrayList<>();

    // 定义一个内部类来表示一个时间段，更清晰
    private static class TimeSlot {
        String startTime; // "HH:mm"
        String endTime;   // "HH:mm"

        TimeSlot(String start, String end) {
            this.startTime = start;
            this.endTime = end;
        }
    }

    /**
     * 【【核心大改造】】
     * 公共静态方法，用于解析后台返回的 JSON 字符串并更新规则列表
     * @param jsonArrayStr 包含时间段对象的 JSON 数组字符串
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

    private final Runnable timeCheckRunnable = new Runnable() {
        @Override
        public void run() {
            boolean shouldBeLocked = checkIfInLockTime();
            if (shouldBeLocked != isCurrentlyLocked) {
                isCurrentlyLocked = shouldBeLocked;
                sendLockStatusBroadcast(isCurrentlyLocked);
            }
            handler.postDelayed(this, 60 * 1000);
        }
    };
    // --- 时间控制核心模块 END ---

    // ... (从这里到 buildNotification() 方法之间的代码和之前一样，没有变化) ...
    public static void start(Players player) {
        ContextCompat.startForegroundService(App.get(), new Intent(App.get(), PlaybackService.class));
        PlaybackService.player = player;
    }

    public static void stop() {
        App.get().stopService(new Intent(App.get(), PlaybackService.class));
    }

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
        if (nonNull() && player.isPlaying()) return buildNotificationAction(androidx.media3.ui.R.drawable.exo_icon_pause, androidx.media3.ui.R.string.exo_controls_pause_description, ActionEvent.PAUSE);
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
        if (nonNull()) builder.setStyle(new MediaStyle().setMediaSession(player.getSession().getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        if (getArt() != null) setIconColor(builder, getArt());
        addAction(builder);
        return builder.build();
    }
    // ... (到这里结束，上面的代码没有变化) ...

    private void setIconColor(NotificationCompat.Builder builder, Bitmap art) {
        builder.setLargeIcon(art);
        Palette palette = Palette.from(art).generate();
        int white = ContextCompat.getColor(this, R.color.white);
        builder.setColor(palette.getMutedColor(palette.getVibrantColor(white)));
    }

    // 作者: 婉儿
    // --- 时间控制辅助方法 START ---

    /**
     * 【【核心大改造】】
     * 检查当前时间是否落在任何一个锁定的时间段内
     */
    private boolean checkIfInLockTime() {
        synchronized (lockTimeSlots) {
            if (lockTimeSlots.isEmpty()) return false;

            Calendar now = Calendar.getInstance();
            int currentHour = now.get(Calendar.HOUR_OF_DAY);
            int currentMinute = now.get(Calendar.MINUTE);

            for (TimeSlot slot : lockTimeSlots) {
                try {
                    String[] startParts = slot.startTime.split(":");
                    String[] endParts = slot.endTime.split(":");
                    int startHour = Integer.parseInt(startParts[0]);
                    int startMinute = Integer.parseInt(startParts[1]);
                    int endHour = Integer.parseInt(endParts[0]);
                    int endMinute = Integer.parseInt(endParts[1]);

                    boolean isMatch;
                    if (startHour > endHour || (startHour == endHour && startMinute > endMinute)) { // 跨天
                        isMatch = (currentHour > startHour || (currentHour == startHour && currentMinute >= startMinute)) ||
                                  (currentHour < endHour || (currentHour == endHour && currentMinute < endMinute));
                    } else { // 不跨天
                        isMatch = (currentHour > startHour || (currentHour == startHour && currentMinute >= startMinute)) &&
                                  (currentHour < endHour || (currentHour == endHour && currentMinute < endMinute));
                    }

                    if (isMatch) {
                        return true; // 只要匹配到一个时间段，就立刻返回 true
                    }
                } catch (Exception e) {
                    Log.e("Waner", "解析单个时间段时出错: " + slot.startTime + "-" + slot.endTime, e);
                    // 继续检查下一个时间段
                }
            }
            return false; // 检查完所有时间段都不匹配
        }
    }

    private void sendLockStatusBroadcast(boolean shouldLock) {
        Intent intent = new Intent("com.fongmi.android.tv.LOCK_STATUS_CHANGED");
        intent.putExtra("isLocked", shouldLock);
        sendBroadcast(intent);
    }

    /**
     * 【【【 哥哥，看这里！ 】】】
     * 在这里实现你从服务器获取时间规则的网络请求
     */
    private void fetchLockTimeRuleFromServer() {
        Log.d("Waner", "准备从服务器获取时间规则...");
        // --- 伪代码示例 START ---
        // 这里要换成你项目里真实的网络请求代码
        // YourApiClient.get("/api/getLockTimeRule", new TextHttpResponseHandler() {
        //     @Override
        //     public void onSuccess(String responseString) {
        //         // responseString 应该是这样的格式:
        //         // "[{\"startTime\":\"08:00\",\"endTime\":\"09:00\"},{\"startTime\":\"12:00\",\"endTime\":\"14:00\"}]"
        //         PlaybackService.updateLockTimeRule(responseString);
        //     }
        //
        //     @Override
        //     public void onFailure(Throwable e) {
        //         Log.e("Waner", "从服务器获取时间规则失败", e);
        //     }
        // });
        // --- 伪代码示例 END ---

        // --- 婉儿帮你加个测试用的假数据，方便你先调试 START ---
        String fakeJson = "[{\"startTime\":\"08:00\",\"endTime\":\"09:00\"},{\"startTime\":\"12:00\",\"endTime\":\"14:00\"},{\"startTime\":\"21:00\",\"endTime\":\"23:59\"}]";
        PlaybackService.updateLockTimeRule(fakeJson);
        // --- 婉儿帮你加个测试用的假数据，方便你先调试 END ---
    }
    // --- 时间控制辅助方法 END ---

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (event.isUpdate()) Notify.show(buildNotification());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
        fetchLockTimeRuleFromServer();
        handler.post(timeCheckRunnable);
    }

    @Override
    @SuppressLint("ForegroundServiceType")
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (nonNull()) MediaButtonReceiver.handleIntent(player.getSession(), intent);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, Notify.ID, buildNotification(), type);
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timeCheckRunnable);
        EventBus.getDefault().unregister(this);
        getManager().cancel(Notify.ID);
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
