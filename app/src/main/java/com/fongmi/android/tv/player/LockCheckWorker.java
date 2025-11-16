    package com.fongmi.android.tv.player;

    import android.content.Context;

    import androidx.annotation.NonNull;
    import androidx.work.Worker;
    import androidx.work.WorkerParameters;

    import com.fongmi.android.tv.App;
    import com.fongmi.android.tv.event.ShowLockScreenEvent;
    import com.fongmi.android.tv.utils.Prefers;
    import com.fongmi.android.tv.utils.Utils;

    import org.greenrobot.eventbus.EventBus;

    public class LockCheckWorker extends Worker {

        public LockCheckWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            if (shouldBeLocked()) {
                EventBus.getDefault().post(new ShowLockScreenEvent());
            }
            return Result.success();
        }

        private boolean shouldBeLocked() {
            if (Utils.isDev() || !Prefers.isLock()) return false;
            long lockedTime = Prefers.getLockTime();
            if (lockedTime == 0) return false;
            long currentTime = System.currentTimeMillis();
            return currentTime - App.get().getLatestActivityTime() > lockedTime;
        }
    }
