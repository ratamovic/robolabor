package com.codexperiments.robolabor.task.android;

import static com.codexperiments.robolabor.task.android.TaskManagerAndroidException.mustBeExecutedFromUIThread;
import android.os.Handler;
import android.os.Looper;

import com.codexperiments.robolabor.task.TaskScheduler;

public class TaskSchedulerAndroidUI implements TaskScheduler {
    private Handler mUIQueue;
    private Looper mUILooper;

    public TaskSchedulerAndroidUI() {
        super();
        mUILooper = Looper.getMainLooper();
        mUIQueue = new Handler(mUILooper);
    }

    @Override
    public void checkCurrentThread() {
        if (Looper.myLooper() != mUILooper) throw mustBeExecutedFromUIThread();
    }

    @Override
    public void schedule(Runnable pRunnable) {
        mUIQueue.post(pRunnable);
    }

    @Override
    public void scheduleIfNecessary(Runnable pRunnable) {
        if (Looper.myLooper() == mUILooper) {
            pRunnable.run();
        } else {
            mUIQueue.post(pRunnable);
        }
    }
}
